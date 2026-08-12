(ns totenmark.http.handlers.health
  (:require [totenmark.db.config :as db]
            [totenmark.http.response :as response]))

(defn live
  [_request]
  (response/json-response 200 {:status "ok"}))

(defn ready
  [_request]
  (try
    (if (db/ready?)
      (response/json-response 200 {:status "ready"})
      (response/json-response 503 {:status "not-ready"}))
    (catch Exception exception
      (response/log-error "readiness check" exception)
      (response/json-response 503 {:status "not-ready"}))))
