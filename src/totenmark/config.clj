(ns totenmark.config
  (:require [clojure.string :as str]))

(defn- setting
  [environment-name property-name]
  (let [value (or (System/getenv environment-name)
                  (System/getProperty property-name))]
    (when-not (str/blank? value)
      value)))

(defn- integer-setting
  [environment-name property-name default valid? expected]
  (let [raw (or (setting environment-name property-name) default)
        value (parse-long raw)]
    (if (and value (valid? value))
      value
      (throw (ex-info (str environment-name " deve ser " expected ".")
                      {:setting environment-name
                       :value raw})))))

(defn port
  []
  (integer-setting "TOTENMARK_PORT" "totenmark.port" "8890"
                   #(<= 1 % 65535)
                   "um inteiro entre 1 e 65535"))

(defn database-name
  []
  (or (setting "TOTENMARK_DB_NAME" "totenmark.db.name") "totenmark"))

(defn jwt-secret
  []
  (or (setting "TOTENMARK_JWT_SECRET" "totenmark.jwt.secret")
      (throw (ex-info "Configure TOTENMARK_JWT_SECRET antes de iniciar a aplicação."
                      {:setting "TOTENMARK_JWT_SECRET"}))))

(defn jwt-ttl-seconds
  []
  (integer-setting "TOTENMARK_JWT_TTL_SECONDS"
                   "totenmark.jwt.ttl-seconds"
                   "3600"
                   pos?
                   "um inteiro positivo"))

(defn reservation-ttl-seconds
  []
  (integer-setting "TOTENMARK_RESERVATION_TTL_SECONDS"
                   "totenmark.reservation.ttl-seconds"
                   "86400"
                   pos?
                   "um inteiro positivo"))

(defn allowed-origins
  []
  (let [raw (or (setting "TOTENMARK_ALLOWED_ORIGINS"
                         "totenmark.allowed-origins")
                "http://localhost:3000,http://localhost:5173")]
    (->> (str/split raw #",")
         (map str/trim)
         (remove str/blank?)
         set)))
