(ns c6h6.hook
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.tools.macro :refer [name-with-attributes]]
            [clj-http.client :as http]
            [clojure.data.json :as json])
  (:require [c6h6.utils :as u :refer [success fail defhandler]]
            [c6h6.models :as models]))

(defn format-msg
  "nil return empty string"
  ([msg] (format-msg msg 1024))
  ([msg length]
   (if msg
     (let [orig-len (count msg)
           replaced (-> msg
                      (subs 0 (min length orig-len))
                      ; github use \r\n, we replaced with two space for "..." append
                      (str/replace "\r\n" "  ")
                      (str/replace "\n" " "))
           replaced-len (count replaced)]
       (-> (if (= orig-len replaced-len)
             replaced
             (str (subs replaced 0 (- (count replaced) 3))
                  "..."))
         (str/trim)))
     ""))) ; ensure length not exceed specified

(defmulti forward-event (fn [event req uid] event))

(defmulti forward-issues (fn [action & _] action))

(defmethod forward-issues "opened"
  [action issue uid]
  (let [{issue-url :html_url :keys [title body sender number]} issue
        body (json/write-str {:resource_id (str number)
                              :content (str body " " issue-url)
                              :status "default"})
        _ (log/debug "body is " body)
        resp (http/post (str "https://hook2do.herokuapp.com/channel/todos/" uid "/?format=json")
                        {:headers {"Content-Type" "application/json"}
                         :body body})]
    (log/debug "in forward-issues opened resp " resp)
    (success {:msg "pused"})))

(defmethod forward-issues "closed"
  [action issue uid]
  (let [{issue-url :html_url :keys [title body sender number]} issue
        body (json/write-str {:content (str body " " issue-url)
                              :status "archived"})
        url (str "https://hook2do.herokuapp.com/channel/todos/"
                 uid
                 "/"
                 (str number)
                 "/?format=json")
        _ (log/debug "body is " body "\n"
                     "url" url "\n"
                     )
        resp (http/put url
                       {:headers {"Content-Type" "application/json"}
                        :body body})]
    (log/debug "in forward-issues opened resp " resp)
    (success {:msg "pused"})))

(defmethod forward-issues :default
  [action issue uid]
  (success {:msg (str "issue " action " not implemented")}))

(defmethod forward-event "issues"
  [event req uid]
  (let [{params :params} req
        {:keys [issue action repository assignee]} params
        {issue-url :html_url :keys [title body sender number]} issue
        {repo-name :name repo-url :html_url} repository
        {sender-name :login sender-url :html_url} sender
        body (format-msg body)
        title (format-msg title 80)]
    (log/debug "in issues action" action)
    (forward-issues action issue uid)))

(defmethod forward-event :default
  [event req uid]
  (success {:msg (str event " received")}))

(defhandler hook-for-github
  [uid req]
  (let [{headers :headers} req
        _ (clojure.pprint/pprint req)
        github-event (headers "x-github-event")
        req (if (= (get headers "content-type")
                   "application/json")
              req
              (update-in req
                         [:params] ; this is kind of ugly
                         (fn [orig]
                           (merge orig
                                  (let [payload-str (-> req :params :payload)]
                                    (try
                                      (json/read-str payload-str :key-fn keyword)
                                      (catch Exception e
                                        nil)))))))]
    (forward-event github-event req uid)))
