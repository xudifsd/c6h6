(ns c6h6.models
  (:use [korma.core]
        [korma.db :only [defdb postgres]])
  (:require [environ.core :refer [env]]))

(defn- convert-db-uri [db-uri]
  (let [[_ user password host port db] (re-matches #"postgres://(?:(.+):(.*)@)?([^:]+)(?::(\d+))?/(.+)" db-uri)]
    {:user user
     :password password
     :host host
     :port (or port 80)
     :db db
     }))

(def db-spec (postgres
               (convert-db-uri
                 (str
                   (env :database-url "postgres://zqhlmsndpyixet:AjuT3vC6sAVkJt1ymiU_Xklj_c@ec2-54-83-5-151.compute-1.amazonaws.com:5432/db17j2j9m9pus")
                   "?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory"))))

(defdb prod (postgres {
             :host "ec2-54-83-5-151.compute-1.amazonaws.com"
             :db "db17j2j9m9pus"
             :user "zqhlmsndpyixet"
             :port "5432"
             :password "AjuT3vC6sAVkJt1ymiU_Xklj_c"
                       }))

(defentity sayings
  (pk :id)
  (table :saying))

(defn get-saying-by-id [id]
  (first (select sayings
                 (where {:id id}))))

(defn create-saying [content]
  (let [{id :GENERATED_KEY} (insert sayings (values {:content content}))]
    (get-saying-by-id id)))

(defn gets-saying []
  (select sayings))
