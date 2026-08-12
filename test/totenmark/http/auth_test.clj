(ns totenmark.http.auth-test
  (:require [clojure.test :refer [deftest is]]
            [totenmark.db.tokens :as db.tokens]
            [totenmark.db.users :as db.users]
            [totenmark.http.auth :as auth]
            [totenmark.utils.jwt :as jwt]))

(def enter (:enter auth/authenticate))

(deftest rejects-missing-token
  (doseq [authorization [nil "Basic token" "Bearer" "Bearer    "]]
    (is (= 401
           (get-in (enter {:request {:headers (cond-> {}
                                                  authorization
                                                  (assoc "authorization"
                                                         authorization))}})
                   [:response :status])))))

(deftest accepts-valid-token
  (with-redefs [jwt/verify (constantly {:user-id 9 :session-version 2})
                db.tokens/revoked? (constantly false)
                db.users/auth-state (constantly {:id 9 :session-version 2})]
    (let [context (enter {:request {:headers {"authorization" "Bearer token"}}})]
      (is (= 9 (get-in context [:request :identity :user-id])))
      (is (nil? (:response context))))))

(deftest rejects-token-from-an-older-password
  (with-redefs [jwt/verify (constantly {:user-id 9 :session-version 1})
                db.tokens/revoked? (constantly false)
                db.users/auth-state (constantly {:id 9 :session-version 2})]
    (is (= 401
           (get-in (enter {:request {:headers {"authorization" "Bearer old-token"}}})
                   [:response :status])))))

(deftest accepts-token-created-before-session-versioning
  (with-redefs [jwt/verify (constantly {:user-id 9 :session-version 0})
                db.tokens/revoked? (constantly false)
                db.users/auth-state (constantly {:id 9 :session-version 0})]
    (is (= 9
           (get-in (enter {:request {:headers {"authorization" "Bearer old-token"}}})
                   [:request :identity :user-id])))))

(deftest rejects-invalid-token
  (with-redefs [jwt/verify (fn [_] (throw (ex-info "invalid" {})))]
    (is (= 401
           (get-in (enter {:request {:headers {"authorization" "Bearer bad"}}})
                   [:response :status])))))

(deftest distinguishes-session-storage-failure-from-an-invalid-token
  (with-redefs [jwt/verify (constantly {:user-id 9
                                        :session-version 0
                                        :jti "token"})
                db.users/auth-state (fn [_]
                                      (throw (ex-info "database unavailable" {})))]
    (let [context (binding [*err* (java.io.StringWriter.)]
                    (enter {:request {:headers {"authorization"
                                                "Bearer valid-token"}}}))]
      (is (= 500 (get-in context [:response :status]))))))
