(ns totenmark.http.rate-limit
  (:require [totenmark.http.response :as response]))

(defn- epoch-seconds
  []
  (quot (System/currentTimeMillis) 1000))

(defn- active-buckets
  [buckets now window-seconds]
  (into {}
        (filter (fn [[_ {:keys [started-at]}]]
                  (< (- now started-at) window-seconds)))
        buckets))

(defn- admit!
  [buckets key limit window-seconds]
  ;; Este limitador é local ao processo. O lock deixa a decisão fácil de auditar
  ;; e é suficiente para os dois endpoints de baixo volume que o utilizam.
  (locking buckets
    (let [now (epoch-seconds)
          state (swap! buckets active-buckets now window-seconds)
          bucket (get state key {:started-at now :count 0})
          retry-after (max 1 (- window-seconds (- now (:started-at bucket))))]
      (if (< (:count bucket) limit)
        (do
          (swap! buckets assoc key (update bucket :count inc))
          {:allowed? true})
        {:allowed? false
         :retry-after retry-after}))))

(defn limiter
  [{:keys [name limit window-seconds]}]
  (let [buckets (atom {})]
    {:name name
     :enter
     (fn [context]
       (let [client (or (get-in context [:request :remote-addr]) "unknown")
             {:keys [allowed? retry-after]}
             (admit! buckets client limit window-seconds)]
         (if allowed?
           context
           (assoc context :response
                  (-> (response/json-response
                       429
                       {:msg "Muitas tentativas. Aguarde um pouco e tente novamente."})
                      (assoc-in [:headers "Retry-After"] (str retry-after)))))))}))

(defn signup
  []
  (limiter {:name ::signup
            :limit 10
            :window-seconds 60}))

(defn login
  []
  (limiter {:name ::login
            :limit 20
            :window-seconds 60}))
