(ns signet.core
  (:require [strokes :refer [d3]]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
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


(defn generate-positions [g]
  [50 150 350])


(defn draw-graph
  "doc-string"
  [graph-data frame]
  (let [width 700
        height 300
        svg (.. d3
                (select frame)
                (append "svg")
                (attr {:width width
                       :height height}))
        data-positions (generate-positions graph-data)
        circle (.. svg
                   (selectAll "circle")
                   (data data-positions)
                   enter
                   (append "circle")
                   (attr {:cx (fn [d] d)
                          :cy (/ height 2)
                          :fill "steelblue"
                          :r 10}))
        links (.. svg
                  (selectAll "link")
                  enter
                  (append "line")
                  (attr {:x1 50
                         :x2 50
                         :y1 (/ height 2)
                         :y2 (/ height 2)
                         }))]
    links))


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
      (println (pr-str app))
      (dom/h1 nil (:text app)))))


(om/root
 commit-graph-view
 app-state
 {:target (. js/document (getElementById "center-container"))})
