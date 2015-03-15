(ns signet.core
  (:require [strokes :refer [d3]]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [signet.graph :refer [compute-positions]]
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
   {:graph-data [1 2 3]
    :text "commit graph"}))


(defn clear-canvas [frame]
  (.. d3
      (select frame)
      (select "svg")
      remove))


(defn draw-graph
  "doc-string"
  [graph-data frame]
  (let [width (* 0.4 (.-width js/screen))
        height (* 0.5 (.-height js/screen))
        circle-size 10
        {:keys [nodes x-positions y-positions links branches]}
        (compute-positions width height circle-size test-cg)
        svg (.. d3
                (select frame)
                (append "svg")
                (attr {:width width
                       :height height}))
        tooltip (.. svg
                    (append "text")
                    (style {:visibility "hidden"
                            :position "absolute"
                            :text-anchor "middle"
                            :color "black"}))]
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
                 :fill (fn [d] (if (contains? (into #{} (vals branches)) d)
                                "red"
                                "steelblue"))
                 :r circle-size})
          (on "mouseover" (fn [d] (do (.. tooltip
                                     (style {:visibility "visible"})
                                     (attr {:y (- (get y-positions d) 15)
                                            :x (get x-positions d)})
                                     (text d)))))
          (on "mouseout" (fn [d] (do (.. tooltip
                                     (style {:visibility "hidden"})
                                     (attr {:y (- (get y-positions d) 15)
                                            :x (get x-positions d)})
                                     (text d)))))
          ))))


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
                        (draw-graph [1 2 3]  "#graph-container"))
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
