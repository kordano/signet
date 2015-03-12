(ns signet.graph
  (:require [clojure.set :refer [difference]]))

(defn branches->nodes [c causal-order heads]
  (loop [parents (get causal-order c)
         node c
         order (list c)]
    (if (nil? parents)
      order
      (if (< 2 (count parents))
        (recur (get causal-order  (first parents))
               (first parents)
               (conj order node))
        (let [next-node (first (remove heads parents))]
          (recur (get causal-order next-node)
                 next-node
                 (conj order next-node)))))))


(defn distinct-nodes [{:keys [nodes] :as cg}]
  (assoc cg
    :nodes
    (loop [[b l-order] (first nodes)
           b-list (rest nodes)
           links []]
      (if (empty? b-list)
        (into {} (conj links [b l-order]))
        (let [branch-diffs (map (fn [[k v]] [k (difference (set v) (set l-order))]) b-list)]
          (recur
           (first branch-diffs)
           (rest branch-diffs)
           (conj links [b (set l-order)])))))))


(defn commit-graph->nodes [cg]
  (let [{:keys [branches causal-order]} cg
        heads (into #{} (vals branches))]
    (assoc cg :nodes
           (loop [[b c] (first branches)
                  b-list (rest branches)
                  result []]
             (if-not b
               (sort-by second #(> (count %1) (count %2)) result)
               (recur (first b-list)
                      (rest b-list)
                      (conj result [b (branches->nodes c causal-order heads)])))))))

(defn nodes->order
  "Calculate commit order in time"
  [{:keys [nodes causal-order branches] :as cg}]
  (let [new-nodes (map
                   (fn [[b b-nodes]]
                     [b (branches->nodes
                         (get branches b)
                         (select-keys causal-order b-nodes)
                         (into #{} (vals branches)))])
                   nodes)]
    (assoc cg
      :nodes
      (->> new-nodes
           (map (fn [[k v]] [k (vec (rest v))]))
           (into {}))
      :branch-links
      (->> new-nodes
           (map (fn [[k v]] [k (if (nil? (first v))
                                nil
                                (vec (take 2 v)))]))
           (into {})))))


(defn find-merge-links [{:keys [causal-order branches] :as cg}]
  (let [branch-heads (into {} (map (fn [[k v]] [v k]) branches))]
    (assoc cg :merge-links
      (->> (select-keys causal-order
                (for [[k v] causal-order
                      :when (> (count v) 1)]
                  k))
           (into {})
           (map (fn [[k v]] (map (fn [b] [b [(branches b) k]]) (remove nil? (map branch-heads v)))))
           (apply concat)
           (into {})))))


(defn nodes->links [{:keys [nodes] :as cg}]
  (assoc cg
    :links
    (->> nodes
         (map
          (fn [[k v]]
            [k
             (mapv
              (fn [i]
                [(get v i) (get v (inc i))])
              (range (count v)))]))
         (into {}))))


(defn explore-commit-graph
  "Run the pipeline"
  [cg]
  (->> cg
       commit-graph->nodes
       distinct-nodes
       nodes->order
       find-merge-links
       nodes->links))
