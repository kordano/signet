(ns signet.core
  (:require [strokes :refer [d3]]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [signet.graph :refer [explore-commit-graph]]
            [chord.client :refer [ws-ch]]
            [om.core :as om :include-macros true]
            [om.dom :as dom]
            [kioo.om :refer [content set-attr do-> substitute listen remove-attr add-class remove-class]]
            [kioo.core :refer [handle-wrapper]]
            [cljs.reader :refer [read-string] :as read])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [kioo.om :refer [defsnippet deftemplate]]))


(.log js/console "Kneel before Kordano!")

(strokes/bootstrap)

(enable-console-print!)

(def test-cg
    {:causal-order {10 []
                    20 [10]
                    30 [20]
                    40 [20]
                    50 [40]
                    60 [30 50]
                    70 [60]
                    80 [30]
                    90 [80]
                    100 [70 140]
                    110 [100]
                    120 [90]
                    130 [30]
                    140 [130]}
     :branches {"master" 110
                "fix" 50
                "dev" 120
                "fix-2" 140}})

(def app-state
  (atom
   {:graph-data {:commits {10 #{}
                           20 #{10}
                           30 #{20}
                           40 #{20}
                           50 #{40}
                           60 #{50 30}
                           70 #{60}}
                 :branches {"master" 10
                            "slave" 40}}
    :text "commit graph"}))


(defn clear-canvas [frame]
  (.. d3
      (select frame)
      (select "svg")
      remove))


(defn compute-pos [w h cs]
  (let [c {40 {:branch "fix" :root false :head false
               :order [40 50] :bp 20 :mp 60}
           10 {:branch "master" :root true :head true
               :order [10 20 30 60 70] :bp nil :mp nil}
           80 {:branch "dev" :root false :head true
               :order [80 90 ] :bp 30 :mp nil}
           110 {:branch "dev" :root false :head true
               :order [110 120] :bp 60 :mp nil}
           }
        y-order [40 110 10 80]]
    (loop [x-order (list 10 40 80 110)
           x-positions {}]
      (if (empty? x-order)
        (let [m (count y-order)
              dy (/ (- h (* 2 cs)) m )]
          {:nodes [10 20 30 40 50 60 70 80 90 110 120]
           :links [[10 20] [20 30] [30 60] [60 70] [40 50] [80 90] [20 40] [50 60] [30 80] [60 110] [110 120]]
           :x-positions x-positions
           :y-positions (->> (range m)
                             (map
                              (fn [i]
                                (->> (get-in c [(get y-order i) :order])
                                     (map (fn [id] [id (+ cs (/ dy 2) (* i dy))]))
                                     (into {}))) )
                             (apply merge))})
        (let [{:keys [order head root bp mp]} (get c (peek x-order))
              n (count order)
              start (or (get x-positions bp) cs)
              end (or (get x-positions mp) (- w cs))
              dx (/ (- end start) (if root
                                    (if head (dec n) n)
                                    (if head n (inc n))))
              offset (if bp dx 0)
              b-positions (->> (range n)
                               (map (fn [i] [(get order i) (+ start offset (* i dx))]))
                               (into {}))]
          (recur (pop x-order) (merge x-positions b-positions)))))))


(defn draw-graph
  "doc-string"
  [graph-data frame]
  (let [width (* 0.4 (.-width js/screen))
        height (* 0.5 (.-height js/screen))
        circle-size 10
        {:keys [nodes x-positions y-positions links]} (compute-pos width height circle-size)
        svg (.. d3
                (select frame)
                (append "svg")
                (attr {:width width
                       :height height}))]
    (do
      (.. svg
          (selectAll "link")
          (data links)
          enter
          (append "line")
          (attr {:x1 (fn [[v1 _]] (x-positions v1))
                 :y1 (fn [[v1 _]] (y-positions v1))
                 :x2 (fn [[_ v2]] (x-positions v2))
                 :y2 (fn [[_ v2]] (y-positions v2))})
          (style {:stroke-with 2
                  :stroke "black"}))
      (.. svg
          (selectAll "circle")
          (data nodes)
          enter
          (append "circle")
          (attr {:cx (fn [d] (get x-positions d))
                 :cy (fn [d] (get y-positions d))
                 :fill "steelblue"
                 :r circle-size})))))


(defn commit-graph-view
  "Force-based graphs with selection"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:ws-ch nil
       :selected "0"})
    om/IWillMount
    (will-mount [_]
      (go
        (let [{:keys [ws-channel error] :as ws-conn} (<! (ws-ch "ws://localhost:8091/data/ws"))]
          (if-not error
            (do
              (om/set-state! owner :ws-ch ws-channel)
              (>! ws-channel {:topic :graph :data :all})
              (loop [{msg :message err :error} (<! ws-channel)]
                (if-not err
                  (do (om/transact! app :graph-data (fn [old] old))
                      (go
                        (clear-canvas "#graph-container")
                        (draw-graph [50 100 150] "#graph-container"))
                      (if-let [new-msg (<! ws-channel)]
                        (recur new-msg)))
                  (println "Error: " (pr-str err)))))
            (println "Error")))))
    om/IRenderState
    (render-state [app this]
      (dom/h1 nil (:text app)))))


(om/root
 commit-graph-view
 app-state
 {:target (. js/document (getElementById "center-container"))})


(println (explore-commit-graph test-cg))
