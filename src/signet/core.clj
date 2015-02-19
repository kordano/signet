(ns signet.core
  (:require [hasch.core :refer [uuid]]
            [konserve.store :refer [new-mem-store]]
            [konserve.protocols :refer [-get-in -assoc-in]]
            [taoensso.timbre :as timbre]
            [geschichte.sync :refer [server-peer client-peer]]
            [geschichte.stage :as s]
            [geschichte.p2p.fetch :refer [fetch]]
            [geschichte.p2p.hash :refer [ensure-hash]]
            [geschichte.p2p.fetch :refer [fetch]]
            [geschichte.p2p.hooks :refer [hook]]
            [geschichte.p2p.publish-on-request :refer [publish-on-request]]
            [geschichte.p2p.block-detector :refer [block-detector]]
            [geschichte.platform :refer [create-http-kit-handler! <!? start]]
            [clojure.core.async :refer [>!! <!!]]
            [datomic.api :as d]))

(timbre/refer-timbre)

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
     conn)})


(defn db-transact [conn txs]
  (debug "TRANSACT DATOMIC:"
         @(d/transact
           conn
           (map #(assoc % :db/id (d/tempid :db.part/user))
                txs)))
  conn)


(defn mapped-eval [code]
  (if (eval-map code)
    (eval-map code)
    (do (debug "eval-map didn't match:" code)
        (eval code))))


(defn find-fn [name]
  (first (filter (fn [[_ fn-name]]
                   (= name fn-name))
                 (keys eval-map))))


(defn init-repo [config]
  (let [{:keys [user repo branches store remote peer]} config
        peer-server (server-peer (create-http-kit-handler! peer) ;; TODO client-peer?
                                 store
                                 (comp (partial block-detector :peer-core)
                                       (partial fetch store)
                                       ensure-hash
                                       (partial publish-on-request store)
                                       (partial block-detector :p2p-surface)))
        #_(client-peer "benjamin"
                       store
                       (comp (partial fetch store)
                             ensure-hash
                             (partial publish-on-request store)))
        stage (<!? (s/create-stage! user peer-server eval))
        res {:store store
             :peer peer-server
             :stage stage
             :id repo}]
    (when-not (= peer :client)
      (start peer-server))
    (when remote
      (<!? (s/connect! stage remote)))
    (when repo
      (<!? (s/subscribe-repos! stage {user {repo branches}})))
    res))


(comment

  (def uri "datomic:mem://signet")

  (d/create-database uri)

  (def conn (d/connect uri))

  (def schema
    [{:db/id #db/id[:db.part/db]
      :db/ident :bookmark/title
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :db/fulltext true
      :db/index true
      :db.install/_attribute :db.part/db}
     {:db/id #db/id[:db.part/db]
      :db/ident :bookmark/url
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :db/unique :db.unique/identity
      :db.install/_attribute :db.part/db}
     {:db/id #db/id[:db.part/db]
      :db/ident :bookmark/user
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :db.install/_attribute :db.part/db
      :db/doc "A bookmark's reference to users"}])


  (d/transact conn schema)


  (defn ->datomic-schema [dts]
    (map (fn [d] (assoc (select-keys d #{:content :author :tag :detail-text :url
                                        :ts :topiq :voter :updown})
                  :db/id (d/tempid :db.part/user))) dts))


  (def hooks (atom {[#".*"
                     #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                     "master"]
                    [["eve@polyc0l0r.net"
                      #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                      "master"]]}))

  (def store (<!? (new-mem-store)))

  store

  (def peer (client-peer "datomic-test" store (comp (partial block-detector :server)
                                                    (partial hook hooks store)
                                                    (partial publish-on-request store)
                                                    (partial fetch store)
                                                    ensure-hash)))

  (def eval-fn {'(fn replace [old params] params) (fn replace [old params] (println "replace params: " params) params)
                '(fn [old params] (d/db-with old params))
                (fn [old params]
                  (let [dtxs (->datomic-schema params)]
                    (println "apply params: " params "as: " dtxs)
                    (d/transact conn dtxs))
                  old)})

  (def stage (<!! (s/create-stage! "foo@bar.com" peer eval-fn)))

  ())
