(ns totenmark.http.server
  (:require [io.pedestal.connector :as conn]
            [io.pedestal.http.http-kit :as hk]
            [totenmark.config :as config]
            [totenmark.http.routes :as routes]))

(defn create-connector []
  (-> (conn/default-connector-map (config/port))
      (conn/with-default-interceptors)
      (conn/with-routes routes/routes)
      (hk/create-connector nil)))

(defn start! []
  (config/jwt-secret)
  (conn/start! (create-connector)))
