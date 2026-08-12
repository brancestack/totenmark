(ns totenmark.http.observability
  (:require [clojure.data.json :as json]))

(defn- write-log!
  [event]
  (println (json/write-str event)))

(def interceptor
  {:name ::request-observability
   :enter
   (fn [context]
     (let [request-id (or (get-in context [:request :headers "x-request-id"])
                          (str (random-uuid)))]
       (-> context
           (assoc ::started-at (System/nanoTime))
           (assoc-in [:request :request-id] request-id))))
   :leave
   (fn [context]
     (let [request (:request context)
           response (:response context)
           duration-ms (/ (- (System/nanoTime) (::started-at context)) 1000000.0)
           request-id (:request-id request)]
       (write-log! {:timestamp (str (java.time.Instant/now))
                    :level "info"
                    :event "http-request"
                    :request-id request-id
                    :method (some-> (:request-method request) name)
                    :uri (:uri request)
                    :status (:status response)
                    :duration-ms (double (Math/round duration-ms))})
       (assoc-in context [:response :headers "X-Request-ID"] request-id)))})
