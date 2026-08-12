(ns totenmark.utils.jwt-test
  (:require [buddy.sign.jwt :as buddy-jwt]
            [clojure.test :refer [deftest is testing use-fixtures]]
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
  (let [claims (jwt/verify (jwt/generate {:id 7
                                          :email "alice@example.com"
                                          :session-version 3}))]
    (is (= 7 (:user-id claims)))
    (is (= 3 (:session-version claims)))
    (is (not (contains? claims :email)))
    (is (string? (:jti claims)))
    (is (< (:iat claims) (:exp claims)))))

(deftest rejects-expired-token
  (testing "expiration is checked during verification"
    (is (thrown? Exception
                 (jwt/verify
                  (buddy-jwt/sign {:user-id 7
                                   :session-version 0
                                   :jti "expired-token"
                                   :iat 1
                                   :exp 2}
                                  "a-test-secret-that-is-not-used-in-production"))))))

(deftest validates-required-claims
  (let [secret "a-test-secret-that-is-not-used-in-production"
        now (.getEpochSecond (java.time.Instant/now))
        valid {:user-id 7
               :jti "token-id"
               :iat now
               :exp (+ now 60)}]
    (testing "tokens from before session versioning start at version zero"
      (is (= 0 (:session-version
                (jwt/verify (buddy-jwt/sign valid secret))))))
    (doseq [claims [(dissoc valid :user-id)
                    (assoc valid :user-id 0)
                    (dissoc valid :jti)
                    (assoc valid :jti "")
                    (assoc valid :session-version -1)
                    (assoc valid :exp "later")
                    (assoc valid :exp now)]]
      (is (thrown? Exception
                   (jwt/verify (buddy-jwt/sign claims secret)))))))
