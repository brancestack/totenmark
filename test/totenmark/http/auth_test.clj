(ns totenmark.http.auth-test
  (:require [clojure.test :refer [deftest is]]
            [totenmark.http.auth :as auth]
            [totenmark.utils.jwt :as jwt]))

(def enter (:enter auth/authenticate))

(deftest rejects-missing-token
  (is (= 401 (get-in (enter {:request {:headers {}}}) [:response :status]))))

(deftest accepts-valid-token
  (with-redefs [jwt/verify (constantly {:user-id 9})]
    (let [context (enter {:request {:headers {"authorization" "Bearer token"}}})]
      (is (= 9 (get-in context [:request :identity :user-id])))
      (is (nil? (:response context))))))

(deftest rejects-invalid-token
  (with-redefs [jwt/verify (fn [_] (throw (ex-info "invalid" {})))]
    (is (= 401
           (get-in (enter {:request {:headers {"authorization" "Bearer bad"}}})
                   [:response :status])))))
