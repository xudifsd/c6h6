(ns c6h6.models
  (:use [korma.core]
        [korma.db :only [defdb postgres]])
  (:require [environ.core :refer [env]]))

(defdb prod (postgres {
             :host "ec2-54-83-5-151.compute-1.amazonaws.com"
             :db "db17j2j9m9pus"
             :user "zqhlmsndpyixet"
             :port "5432"
             :password "AjuT3vC6sAVkJt1ymiU_Xklj_c"}))

(defentity thirdparties
  (pk :id)
  (table :thirdparties))

(defn get-thirdparties-by-uid [uid]
  (first (select thirdparties
                 (where {:uid uid}))))

(defn create-thirdparties [thirdparty]
  (insert thirdparties (values thirdparty)))

(defn gets-thirdparties []
  (select thirdparties))
