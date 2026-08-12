(ns totenmark.http.rate-limit-test
  (:require [clojure.test :refer [deftest is]]
            [totenmark.http.rate-limit :as rate-limit]))

(deftest blocks-requests-over-the-limit
  (let [interceptor (rate-limit/limiter {:name ::test
                                         :limit 2
                                         :window-seconds 60})
        enter (:enter interceptor)
        context {:request {:remote-addr "127.0.0.1"}}]
    (is (nil? (:response (enter context))))
    (is (nil? (:response (enter context))))
    (let [response (:response (enter context))]
      (is (= 429 (:status response)))
      (is (some? (get-in response [:headers "Retry-After"]))))))

(deftest separate-limiters-do-not-share-client-history
  (let [first-enter (:enter (rate-limit/limiter {:name ::first
                                                  :limit 1
                                                  :window-seconds 60}))
        second-enter (:enter (rate-limit/limiter {:name ::second
                                                   :limit 1
                                                   :window-seconds 60}))
        context {:request {:remote-addr "127.0.0.1"}}]
    (is (nil? (:response (first-enter context))))
    (is (= 429 (get-in (first-enter context) [:response :status])))
    (is (nil? (:response (second-enter context))))))

(deftest clients-have-independent-buckets
  (let [enter (:enter (rate-limit/limiter {:name ::clients
                                            :limit 1
                                            :window-seconds 60}))]
    (is (nil? (:response (enter {:request {:remote-addr "10.0.0.1"}}))))
    (is (= 429 (get-in (enter {:request {:remote-addr "10.0.0.1"}})
                       [:response :status])))
    (is (nil? (:response (enter {:request {:remote-addr "10.0.0.2"}}))))))

(deftest concurrent-requests-cannot-step-over-the-limit
  (let [limit 5
        enter (:enter (rate-limit/limiter {:name ::concurrent
                                            :limit limit
                                            :window-seconds 60}))
        start (promise)
        calls (repeatedly 30
                          #(future
                             @start
                             (enter {:request {:remote-addr "127.0.0.1"}})))]
    (deliver start true)
    (is (= {nil limit, 429 (- 30 limit)}
           (frequencies
            (map #(get-in (deref %) [:response :status]) calls))))))

(deftest a-new-window-clears-the-old-bucket
  (let [clock (atom 1000)
        epoch-var (ns-resolve 'totenmark.http.rate-limit 'epoch-seconds)]
    (with-redefs-fn
      {epoch-var #(deref clock)}
      (fn []
        (let [enter (:enter (rate-limit/limiter {:name ::window
                                                  :limit 1
                                                  :window-seconds 10}))
              context {:request {:remote-addr "127.0.0.1"}}]
          (is (nil? (:response (enter context))))
          (is (= 429 (get-in (enter context) [:response :status])))
          (reset! clock 1010)
          (is (nil? (:response (enter context)))))))))
