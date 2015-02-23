(ns signet.db
  (:require [hasch.core :refer [uuid]]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [taoensso.timbre :as timber])
  (:import datomic.Util))

(timber/refer-timbre)

(defn db-transact [conn txs]
  (debug "TRANSACT DATOMIC:"
         @(d/transact conn (map #(assoc % :db/id (d/tempid :db.part/user))
                                txs)))
  conn)


(def eval-map
  {'(fn create-db [old {:keys [name]}]
      (let [uri (str "datomic:mem:///" name)]
        (d/create-database uri)
        (d/connect uri)))

   (fn [old init]
     (let [uri (str "datomic:mem:///" name)]
       (d/create-database uri)
       (d/connect uri)))

   '(fn transact-schema [conn schema]
     (d/transact conn schema)
     conn)

   (fn [conn schema] ;; HACK to initialize datomic with the final schema
     (d/transact conn (-> "resources/schema.edn" slurp read-string))
     conn)

   })


(defn mapped-eval [code]
  (if (eval-map code)
    (eval-map code)
    (do (debug "eval-map didn't match:" code)
        (eval code))))


(defn find-fn [name]
  (first (filter (fn [[_ fn-name]]
                   (= name fn-name))
                 (keys eval-map))))
