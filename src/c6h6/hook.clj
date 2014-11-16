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

(defn gen-resource-id [repo-url num]
  (str repo-url "|" num))

(defn parse-resource-id [resource-id]
  (let [[repo-url num] (str/split resource-id #"\|")]
    [repo-url num]))

(defhandler update-issue [uid resource_id state]
  (let [[repo-url num] (parse-resource-id resource_id)
        thirdparty (models/get-thirdparties-by-uid uid)
        url (str "https://api.github.com/repos/" repo-url "/issues/" num)
        resp (http/patch url {:headers {"Authorization" (str "token " (:access_token thirdparty))}
                              :body (json/write-str {:state state})})]
    (if (= (:status resp) 200)
      (success {:msg "ok"})
      (fail (:status resp) "not ok"))))

(defmulti forward-event (fn [event req uid] event))

(defmulti forward-issues (fn [action & _] action))

(defn gen-content [title body url]
  (format "[github] %s\n%s" title url))

(defmethod forward-issues "opened"
  [action issue uid repository]
  (let [{issue-url :html_url :keys [title body sender number]} issue
        {:keys [full_name]} repository
        body (json/write-str {:resource_id (gen-resource-id full_name number)
                              :content (gen-content title body issue-url)
                              :status "default"})
        resp (http/post (str "https://hook2do.herokuapp.com/channel/todos/" uid "/?format=json")
                        {:headers {"Content-Type" "application/json"}
                         :body body})]
    (success {:msg "pused"})))

(defmethod forward-issues "closed"
  [action issue uid repository]
  (let [{issue-url :html_url :keys [title body sender number]} issue
        {:keys [full_name]} repository
        body (json/write-str {:content (gen-content title body issue-url)
                              :status "archived"})
        url (str "https://hook2do.herokuapp.com/channel/todos/"
                 uid
                 "/"
                 (gen-resource-id full_name number)
                 "/?format=json")
        resp (http/put url
                       {:headers {"Content-Type" "application/json"}
                        :body body})]
    (success {:msg "pused"})))

(defmethod forward-issues :default
  [action issue uid repository]
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
    (forward-issues action issue uid repository)))

(defmethod forward-event :default
  [event req uid]
  (success {:msg (str event " received")}))

(defhandler hook-for-github
  [uid req]
  (let [{headers :headers} req
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
