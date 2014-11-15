(ns c6h6.utils
  (:require [clojure.tools.logging :as log]
            [clojure.tools.macro :refer [name-with-attributes]]
            [clojure.data.json :as json]))

(defn response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json;charset=utf-8"
             "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS"}
   :body (json/write-str data)})

(defn success [data]
  (response data 200))

(defn fail [status msg]
  (response {:msg msg} status))

(defmacro defasync [name args & body]
  (let [name-str (str name)]
    `(defn ~name ~args
       (future
         (try
           ~@body
           (catch Exception e#
             (log/error (str ~name-str
                             " error: "
                             e#
                             "\n"
                             (with-out-str
                               (clojure.stacktrace/print-stack-trace e#))))))))))

(defmacro defhandler*
  [name args & body]
  `(defn ~name [req#]
     (let [{:keys ~args :or {~'req req#}} (:params req#)]
       ~@body)))

(defmacro check-params [& clauses]
  (when clauses
    (if-not (even? (count clauses))
      (throw (IllegalArgumentException.
               "check-params requires an even number of forms"))
      `(let [first-clauses# ~(first clauses)]
         (if first-clauses#
           (let [second-clauses# ~(second clauses)]
             (if-not (= :else first-clauses#)
               (fail 400 second-clauses#)
               second-clauses#))
           (check-params ~@(nnext clauses)))))))

(defn has-pre-check? [fdecl]
  (and (map? (first fdecl)) (:pre-check (first fdecl))))

(defmacro create-handler [name & fdecl]
  (let [[args & fdecl] (if (vector? (first fdecl))
                         fdecl
                         (cons [] fdecl))
        [{:keys [pre-check]} & fdecl] (if (has-pre-check? fdecl)
                                        fdecl
                                        (cons nil fdecl))]
    (if pre-check
      `(defhandler* ~name ~args (check-params ~@pre-check :else (do ~@fdecl)))
      `(defhandler* ~name ~args ~@fdecl))))

(defmacro defhandler
  [name & fdecl]
  (let [[name fdecl] (name-with-attributes name fdecl)]
    `(create-handler ~name ~@fdecl)))

(defn ->int [s]
  (try
    (cond
     (string? s) (Integer/parseInt s)
     (instance? Integer s) s
     (instance? Long s) (.intValue ^Long s)
     :else nil)
    (catch Exception e
      nil)))

(defn ->long
  ([s] (->long s nil))
  ([s default]
   (try
     (cond
       (string? s) (Long/parseLong s)
       (instance? Integer s) s
       (instance? Long s) s
       :else default)
     (catch Exception e
       default))))

(defn safe-empty?
  "data like int, keyword, double are considered not empty"
  [x]
  (try
    (empty? x)
    (catch IllegalArgumentException _
      false)))

(defn nil-or-empty?
  [v]
  ((some-fn nil? safe-empty?) v))

(defn dissoc-if-nil-empty
  "(dissoc-if-nil-empty {:a nil :b \"\" :c \"aa\"} [:a :b :c])
  => {:c \"aa\"}"
  [m keys]
  (if (nil-or-empty? keys)
    m
    (if (nil-or-empty? (m (first keys)))
      (recur (dissoc m (first keys)) (rest keys))
      (recur m (rest keys)))))

(defn dissoc-if-nil
  "like dissoc-if-nil-empty except we do not dissoc empty"
  [m keys]
  (if (nil-or-empty? keys)
    m
    (if (nil? (m (first keys)))
      (recur (dissoc m (first keys)) (rest keys))
      (recur m (rest keys)))))

(defmacro create-kw-map
  "(let [a 1 b 2 c 3] (create-kw-map a b c)) => {:a 1 :b 2 :c 3}"
  [& syms]
  `(zipmap (map keyword '~syms) (list ~@syms)))

(defn exception-if-contains [m & keys]
  (if (some #(contains? m %) keys)
    (throw (RuntimeException. (str "shouldn't contains keys " keys " in " m)))
    m))

(defn- json-request?
  [req]
  (if-let [#^String type (:content-type req)]
    (not (empty? (re-find #"^application/(vnd.+)?json" type)))))

(defn wrap-json-params [handler]
  (fn [req]
    (if-let [body (and (json-request? req) (:body req))]
      (let [bstr (slurp body)]
        (if (not-empty bstr)
          (let [json-params (json/read-str bstr :key-fn keyword)
                req* (assoc req
                            :json-params json-params
                            :params (merge (:params req) json-params))]
            (handler req*))
          (handler req)))
      (handler req))))
