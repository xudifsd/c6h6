(ns c6h6.models
  (:require [environ.core :refer [env]]
            [clojure.java.jdbc :as db]
            [clojure.tools.logging :as log]))

(defonce db-env (env :database-url
                     "postgres://zqhlmsndpyixet:AjuT3vC6sAVkJt1ymiU_Xklj_c@ec2-54-83-5-151.compute-1.amazonaws.com:5432/db17j2j9m9pus"))

(defn get-saying-by-id [id]
  (first (db/query db-env
                   ["select * from sayings where id = ?" id])))

(defn create-saying [content]
  (let [{id :GENERATED_KEY :as all}
        (db/insert! db-env
                    :sayings {:content content})]
    (log/debug "in create-saying " all)
    (get-saying-by-id id)))

(defn gets-saying []
  (db/query db-env
            ["select * from sayings"]))
