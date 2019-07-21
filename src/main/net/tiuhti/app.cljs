(ns net.tiuhti.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [testdouble.cljs.csv :as csv]
            [goog.string :as gstr]
            ["js-joda" :as joda]
            ["vega-embed" :as ve]))

(def formatter (joda/DateTimeFormatter.ofPattern "yyyy/MM/dd HH:mm:ss"))

(def vega (atom nil))

(defn init []
  (go (let [indoor-data (->> (csv/read-csv (:body (<! (http/get (str js/window.location "indoor.csv")))) :separator ";")
                             (drop 3)
                             (into [] (comp (map #(zipmap [:epoch :timestamp :temperature :rh :co2 :noise :pressure] %))
                                            (map (fn [row]
                                                   (-> row
                                                       (update :temperature gstr/parseInt)
                                                       (update :rh gstr/parseInt)
                                                       (update :co2 gstr/parseInt)
                                                       (update :noise gstr/parseInt)
                                                       (update :pressure js/Number.parseFloat)
                                                       (update :timestamp #(-> %
                                                                               (joda/LocalDateTime.parse formatter)
                                                                               (joda/convert)
                                                                               (.toDate)))))))))
            outdoor-data (->> (csv/read-csv (:body (<! (http/get (str js/window.location "outdoor.csv")))) :separator ";")
                              (drop 3)
                              (into [] (comp (map #(zipmap [:epoch :timestamp :temperature :rh] %))
                                             (map (fn [row]
                                                    (-> row
                                                        (update :temperature gstr/parseInt)
                                                        (update :rh gstr/parseInt)
                                                        (update :timestamp #(-> %
                                                                                (joda/LocalDateTime.parse formatter)
                                                                                (joda/convert)
                                                                                (.toDate)))))))))]
        (-> (ve/embed "#root"
                      (clj->js {:width 1200
                                :height 600
                                :layer [{:data {:name "temperature"
                                                :values (map #(select-keys % [:timestamp :temperature]) outdoor-data)}
                                         :mark {:type :line}
                                         :encoding {:x {:field :timestamp :type :temporal}
                                                    :y {:field :temperature :type :quantitative}}}
                                        {:selection {:grid {:type "interval"
                                                            :bind "scales"}}
                                         :data {:name "co2"
                                                :values (map #(select-keys % [:timestamp :co2]) indoor-data)}
                                         :mark {:type :line
                                                :color "#FF0000"}
                                         :encoding {:x {:field :timestamp :type :temporal}
                                                    :y {:field :co2 :type :quantitative}}}]
                                :resolve {:scale {:y :independent}}})
                      (clj->js {:actions true}))
            (.then (fn [result] (reset! vega result))))))
  (js/console.log "init"))

(defn ^:dev/before-load stop []
  (.finalize (get (js->clj @vega) "view"))
  (js/console.log "stop"))

(defn ^:dev/after-load start []
  (js/console.log "start")
  (init))
