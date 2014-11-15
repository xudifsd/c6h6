(ns c6h6.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [clojure.tools.macro :refer [name-with-attributes]]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]))

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

(defhandler foo [a b]
  (response {:a a :b b} 200))

(defn splash []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str ["Hello" :from 'Heroku])})

(defroutes app
  (GET "/" [a b]
       foo)
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))
