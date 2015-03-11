(ns signet.graph
  (:require [clojure.set :refer [difference]]
            [aprint.core :refer [aprint]]))

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
                  110 [100]
                  120 [50]}
   :branches {"master" 110
              "fix" 120
              "dev" 90}})


(defn link-order [c causal-order heads]
  (loop [parents (get causal-order c)
         node c
         order (list [c])]
    (if (empty? parents)
      order
      (if (< 2 (count parents))
        (recur (get causal-order  (first parents))
               (first parents)
               (conj order [(first parents) node ]))
        (let [next-node (first (remove heads parents))]
          (recur (get causal-order next-node)
                 next-node
                 (conj order [next-node node ])))))))


(defn commit-graph->links [cg]
  (let [{:keys [branches causal-order]} cg
        heads (into #{} (vals branches))]
    (loop [[b c] (first branches)
           b-list (rest branches)
           result []]
      (if-not b
        (sort-by second #(> (count %1) (count %2)) result)
        (recur (first b-list)
               (rest b-list)
               (conj result [b (link-order c causal-order heads)]))))))

(defn distinct-links [g]
  (loop [[b l-order] (first g)
         b-list (rest g)
         links []]
    (if (empty? b-list)
      (into {} (conj links [b l-order]))
      (let [branch-diffs (map (fn [[k v]] [k (difference (set v) (set l-order))]) b-list)]
        (recur
         (first branch-diffs)
         (rest branch-diffs)
         (conj links [b (set l-order)]))))))


(defn distinct-nodes [g]
  (into {} (map (fn [[k v]] [k (mapv first v)]) g)))



(defn commit-graph->order [cg]
  (let [g (commit-graph->links cg)
        links (distinct-links g)]
    {:nodes (distinct-nodes g)
     :links links
     :x-order (map first links)
     :y-order (loop [old (map first links)
                     new (list)
                     i false]
                (if (empty? old)
                  new
                  (recur (rest old)
                         (if i
                           (concat new (list (first old)))
                           (concat (list (first old)) new))
                         (not i))))}))
