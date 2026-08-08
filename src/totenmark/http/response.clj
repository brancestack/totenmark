(ns totenmark.http.response
  (:require [clojure.data.json :as json]))

(defn json-response
  ([status body]
   {:status status
    :headers {"Content-Type" "application/json; charset=utf-8"}
    :body (json/write-str body)}))

(defn log-error
  [operation ^Throwable exception]
  (binding [*out* *err*]
    (println (str "ERROR " operation ": "
                  (.getName (class exception)) " - " (.getMessage exception)))))

(defn failure
  [operation exception status message]
  (log-error operation exception)
  (json-response status {:msg message}))
