(ns totenmark.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [totenmark.config :as config]))

(defn- with-property
  [name value test-function]
  (let [previous (System/getProperty name)]
    (try
      (if (nil? value)
        (System/clearProperty name)
        (System/setProperty name value))
      (test-function)
      (finally
        (if (nil? previous)
          (System/clearProperty name)
          (System/setProperty name previous))))))

(deftest validates-numeric-settings
  (testing "port boundaries"
    (with-property "totenmark.port" "1" #(is (= 1 (config/port))))
    (with-property "totenmark.port" "65535" #(is (= 65535 (config/port))))
    (doseq [value ["0" "65536" "abc"]]
      (with-property "totenmark.port" value
        #(is (thrown-with-msg? clojure.lang.ExceptionInfo
                               #"TOTENMARK_PORT"
                               (config/port))))))
  (testing "time-to-live values must be positive"
    (doseq [[property read-setting]
            [["totenmark.jwt.ttl-seconds" config/jwt-ttl-seconds]
             ["totenmark.reservation.ttl-seconds"
              config/reservation-ttl-seconds]]]
      (with-property property "0"
        #(is (thrown? clojure.lang.ExceptionInfo (read-setting))))
      (with-property property "60"
        #(is (= 60 (read-setting)))))))

(deftest parses-and-trims-allowed-origins
  (with-property "totenmark.allowed-origins"
    " https://app.example.com, http://localhost:5173,https://app.example.com "
    #(is (= #{"https://app.example.com" "http://localhost:5173"}
            (config/allowed-origins)))))

(deftest requires-a-non-blank-jwt-secret
  (with-property "totenmark.jwt.secret" "   "
    #(is (thrown-with-msg? clojure.lang.ExceptionInfo
                           #"TOTENMARK_JWT_SECRET"
                           (config/jwt-secret)))))
