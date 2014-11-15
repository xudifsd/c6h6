(ns c6h6.utils
  (:require [clojure.tools.logging :as log]
            [clojure.tools.macro :refer [name-with-attributes]]
            [clojure.data.json :as json]))

(defn response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json;charset=utf-8"
             "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS"}
   :body (json/write-str data)})

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
               (fail 400 failure-code second-clauses#)
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
