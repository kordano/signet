(ns signet.graph
  (:require [aprint.core :refer [aprint]]))

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
                  100 [70]
                  110 [100]}
   :branches {"master" 110
              "fix" 50
              "dev" 90}})

(defn commit-graph->order
  [{:keys [causal-order branches] :as cg}]
  (let [heads (into #{} (vals branches))]
    (sort-by second #(> (count %1) (count %2))
             (for [[k v] branches]
               (loop [parents (get causal-order v)
                      order (list v)]
                 (if (empty? parents)
                   [k order]
                   (if (< 2 (count parents))
                     (recur (get causal-order (first parents)) (conj order (first parents)))
                     (let [next-node (first (remove heads parents))]
                       (recur (get causal-order next-node) (conj order next-node))))))))))

;; maximum in the middle
(commit-graph->order test-cg)
