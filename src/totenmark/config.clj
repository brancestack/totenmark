(ns totenmark.config
  (:require [clojure.string :as str]))

(defn- setting
  [environment-name property-name]
  (let [value (or (System/getenv environment-name)
                  (System/getProperty property-name))]
    (when-not (str/blank? value)
      value)))

(defn port
  []
  (parse-long (or (setting "TOTENMARK_PORT" "totenmark.port") "8890")))

(defn database-name
  []
  (or (setting "TOTENMARK_DB_NAME" "totenmark.db.name") "totenmark"))

(defn jwt-secret
  []
  (or (setting "TOTENMARK_JWT_SECRET" "totenmark.jwt.secret")
      (throw (ex-info "TOTENMARK_JWT_SECRET must be configured."
                      {:setting "TOTENMARK_JWT_SECRET"}))))

(defn jwt-ttl-seconds
  []
  (parse-long (or (setting "TOTENMARK_JWT_TTL_SECONDS"
                           "totenmark.jwt.ttl-seconds")
                  "3600")))
