(ns totenmark.utils.jwt-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [totenmark.utils.jwt :as jwt]))

(defn with-jwt-settings
  [test-function]
  (System/setProperty "totenmark.jwt.secret" "a-test-secret-that-is-not-used-in-production")
  (System/setProperty "totenmark.jwt.ttl-seconds" "3600")
  (try
    (test-function)
    (finally
      (System/clearProperty "totenmark.jwt.secret")
      (System/clearProperty "totenmark.jwt.ttl-seconds"))))

(use-fixtures :each with-jwt-settings)

(deftest round-trips-token-claims
  (let [claims (jwt/verify (jwt/generate {:id 7 :email "alice@example.com"}))]
    (is (= 7 (:user-id claims)))
    (is (= "alice@example.com" (:email claims)))
    (is (< (:iat claims) (:exp claims)))))

(deftest rejects-expired-token
  (System/setProperty "totenmark.jwt.ttl-seconds" "-1")
  (testing "expiration is checked during verification"
    (is (thrown? Exception
                 (jwt/verify (jwt/generate {:id 7 :email "alice@example.com"}))))))
