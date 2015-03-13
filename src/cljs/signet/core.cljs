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
   {:graph-data [1 2 3]
    :text "commit graph"}))


(defn clear-canvas [frame]
  (.. d3
      (select frame)
      (select "svg")
      remove))

(defn compute-positions
  "Compute positions using widht, height, circle size and improved commit graph"
  [w h cs icg]
  (let [eos (- w cs)]
    (loop [x-order (:x-order icg)
           x-positions {}]
      (println "Calculating " (first x-order))
      (if (empty? x-order)
        (assoc icg
          :nodes (apply concat (vals (:nodes icg)))
          :links (apply concat (vals (:links icg)))
          :x-positions x-positions
          :y-positions (let [m (count (:y-order icg))
                             dy (/ (- h (* 2 cs)) m )]
                         (->> (range m)
                              (map
                               (fn [i]
                                 (->> (get-in icg [:nodes (get (:y-order icg) i)])
                                      (map (fn [id] [id (+ cs (/ dy 2) (* i dy))]))
                                      (into {}))))
                              (apply merge))))
        (let [branch (first x-order)
              nodes (get-in icg [:nodes branch])
              n (count nodes)
              start (or (first (get-in icg [:branch-links branch])) 0)
              end (or (first (get-in icg [:merge-links branch])) eos)
              dx (/ (- end start) (if (= start 0)
                                    (if (= end eos) (dec n) n)
                                    (if (= end eos) n (inc n))))
              offset (if (= start 0) 0 dx)
              branch-positions (->> (range n)
                                    (map (fn [i] [(get nodes i) (+ start offset (* i dx))]))
                                    (into {}))]
          (recur (rest x-order) (merge x-positions branch-positions)))))))

(defn draw-graph
  "doc-string"
  [graph-data frame]
  (let [width (* 0.4 (.-width js/screen))
        height (* 0.5 (.-height js/screen))
        circle-size 10
        icg (explore-commit-graph test-cg)
        {:keys [nodes x-positions y-positions links]} (compute-positions width height circle-size icg)
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
