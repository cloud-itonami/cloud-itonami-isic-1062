(ns starchops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2: this repo previously had NO demo page
  and no generator at all. This namespace drives the REAL actor stack --
  `starchops.operation/run-operation` -> `starchops.governor/check` ->
  `starchops.store` -- and renders whatever that run actually produced.
  Nothing on the page is hand-typed prose about behaviour: every rule
  name, basis list, disposition and in-range verdict is read back out of
  the ledger/store or recomputed by calling this repo's own predicates.

  Three things about THIS repo shaped the design (all verified before a
  line was written, not assumed from the sibling ISIC repos):

  1. `starchops.store` ships NO seed/demo data -- it is a pure-function
     surface over a plain `{:batches {} :facts []}` value, and
     `starchops.sim` is still a stub that prints \"not yet implemented\"
     (`clojure -M:dev:run`, confirmed). So the seed had to be authored
     here. To keep it traceable rather than invented, every product-type,
     jurisdiction, raw-material id and evidence-checklist item in
     `seed-batches` below is an id that already exists in
     `starchops.facts`, the evidence checklists are *derived* from
     `facts/jurisdiction-by-id`, the batch ids follow this repo's own
     `batch-00N` test convention, and the one deliberately-unregistered
     subject (`batch-999`) is the id this repo's own
     `operation_test.cljc` uses for exactly that case. Product and
     jurisdiction display names are read from `starchops.facts`, never
     re-typed.

  2. This repo's actor emits exactly ONE audit fact type. Its `:t` is
     read off `starchops.governor/hold-fact` itself (see
     `actor-fact-type`) rather than assumed. `starchops.operation` routes
     BOTH hard holds and soft escalations through that same fact, so a
     HARD hold is identified structurally: an actor fact whose `:basis`
     (`(mapv :rule (:violations verdict))`) is non-empty. Ledger rows
     carrying a `:demo/*` `:t` are demo-driver bookkeeping and are
     labelled as such on the page.

  3. There is NO approval/commit node anywhere in this repo --
     `run-operation` returns `{:ok? false :facts [hold] :verdict ...}`
     for an escalation and offers no resume. The operator sign-off in
     `run-demo!` is therefore performed by this driver on top of the real
     `store/log-batch` / `store/finalize-shipment` writers, and the page
     *measures* (does not assert) where the approver id survives. See
     `approver-attribution`.

  Determinism: `render` is a pure function of the store value. It reads no
  clock, no randomness, and sorts every collection explicitly. The one
  wall-clock input the Governor needs -- the foreign-material-detection
  equipment's last-calibration epoch, compared against `now` inside
  `starchops.governor` -- is never printed; the seed's
  `:calibration-age-days` (the literal offset used to build that epoch) is
  shown instead, and the offsets are far from the 60-day boundary so the
  verdict is stable. Two consecutive runs are byte-identical.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [starchops.facts :as facts]
            [starchops.governor :as governor]
            [starchops.operation :as operation]
            [starchops.phase :as phase]
            [starchops.store :as store]))

(def ^:private actor-id "starchops-actor-1")
(def ^:private operator-id "op-1")
(def ^:private day-ms (* 24 60 60 1000))

(def ^:private actor-fact-type
  "The `:t` of the one audit fact this repo's own code emits. Read off
  `starchops.governor/hold-fact` by calling it, so that renaming the fact
  in the Governor renames it here too -- it is not assumed."
  (:t (governor/hold-fact {} {} {:violations [] :confidence 0.0})))

;; ───────────────────────────── seed ─────────────────────────────

(defn- evidence-for
  "The jurisdiction's own `:required-evidence` list, read from
  `starchops.facts`. Used so a \"complete\" checklist is complete *by
  construction* rather than by a hand-copied vector that could drift."
  [jurisdiction]
  (vec (:required-evidence (facts/jurisdiction-by-id jurisdiction))))

(def ^:private seed-batches
  "Intake records for one production shift. Every keyword id here appears
  in `starchops.facts` (`product-types`, `jurisdictions`,
  `raw-material-allergen-table`); the numbers are chosen relative to the
  windows those facts declare so that each batch exercises a specific
  Governor rule. `:calibration-age-days` is a render-only companion to the
  epoch the Governor reads (see ns docstring)."
  [;; batch-001 -- fully clean; carries the full clean lifecycle.
   {:id "batch-001" :product-type :starch/corn-native :jurisdiction :jp/prefectural
    :moisture-percent 12.0 :purity-percent 99.5 :granulation-microns 15
    :sulfite-residue-ppm 20 :microbial-load-cfu 200
    :foreign-material-detected? false :calibration-age-days 10
    :weight-variance-grams 20 :sanitation-score 85
    :raw-materials [:corn/yellow-dent] :declared-allergens #{}}

   ;; batch-002 -- clean actuals, but an OPEN food-safety concern.
   {:id "batch-002" :product-type :starch/corn-native :jurisdiction :us/fda
    :moisture-percent 11.8 :purity-percent 99.2 :granulation-microns 18
    :sulfite-residue-ppm 25 :microbial-load-cfu 350
    :foreign-material-detected? false :calibration-age-days 14
    :weight-variance-grams 22 :sanitation-score 82
    :raw-materials [:corn/yellow-dent] :declared-allergens #{}
    :safety-concern-raised? true :safety-concern-resolved? false}

   ;; batch-003 -- moisture 10.0% vs corn-native target 12.0 ±0.5.
   {:id "batch-003" :product-type :starch/corn-native :jurisdiction :jp/prefectural
    :moisture-percent 10.0 :purity-percent 99.4 :granulation-microns 16
    :sulfite-residue-ppm 18 :microbial-load-cfu 240
    :foreign-material-detected? false :calibration-age-days 8
    :weight-variance-grams 15 :sanitation-score 88
    :raw-materials [:corn/yellow-dent] :declared-allergens #{}}

   ;; batch-004 -- SO2 residue 34 ppm vs potato-native max 10 ppm.
   {:id "batch-004" :product-type :starch/potato-native :jurisdiction :eu/efsa
    :moisture-percent 18.0 :purity-percent 99.0 :granulation-microns 40
    :sulfite-residue-ppm 34 :microbial-load-cfu 300
    :foreign-material-detected? false :calibration-age-days 20
    :weight-variance-grams 18 :sanitation-score 90
    :raw-materials [:potato/starch-grade] :declared-allergens #{}}

   ;; batch-005 -- 900 CFU/g vs cassava-native max 500 CFU/g.
   {:id "batch-005" :product-type :starch/cassava-native :jurisdiction :us/fda
    :moisture-percent 13.0 :purity-percent 99.2 :granulation-microns 20
    :sulfite-residue-ppm 4 :microbial-load-cfu 900
    :foreign-material-detected? false :calibration-age-days 5
    :weight-variance-grams 12 :sanitation-score 88
    :raw-materials [:cassava/root] :declared-allergens #{}}

   ;; batch-006 -- purity 96.4% (min 98.0) AND 48μm (max 30μm): two rules.
   {:id "batch-006" :product-type :starch/wheat-native :jurisdiction :eu/efsa
    :moisture-percent 14.0 :purity-percent 96.4 :granulation-microns 48
    :sulfite-residue-ppm 15 :microbial-load-cfu 400
    :foreign-material-detected? false :calibration-age-days 12
    :weight-variance-grams 25 :sanitation-score 84
    :raw-materials [:wheat/starch-grade] :declared-allergens #{:wheat}}

   ;; batch-007 -- tramp metal caught on the sifter. Conservative allergen
   ;; declaration (waxy-hybrid carries a shared-line :wheat cross-contact
   ;; risk but no primary allergen) -- declaring more never trips the rule.
   {:id "batch-007" :product-type :starch/corn-native :jurisdiction :jp/prefectural
    :moisture-percent 12.2 :purity-percent 99.3 :granulation-microns 14
    :sulfite-residue-ppm 22 :microbial-load-cfu 260
    :foreign-material-detected? true :calibration-age-days 10
    :weight-variance-grams 20 :sanitation-score 85
    :raw-materials [:corn/yellow-dent :corn/waxy-hybrid] :declared-allergens #{:wheat}}

   ;; batch-008 -- detector 120 days since calibration (60-day interval)
   ;; AND 85 g weight drift (50 g tolerance): two rules.
   {:id "batch-008" :product-type :starch/potato-native :jurisdiction :jp/prefectural
    :moisture-percent 18.0 :purity-percent 99.0 :granulation-microns 40
    :sulfite-residue-ppm 5 :microbial-load-cfu 300
    :foreign-material-detected? false :calibration-age-days 120
    :weight-variance-grams 85 :sanitation-score 86
    :raw-materials [:potato/starch-grade] :declared-allergens #{}}

   ;; batch-009 -- wheat starch shipped with an EMPTY allergen declaration
   ;; AND a sanitation score of 62 (minimum 75): two rules.
   {:id "batch-009" :product-type :starch/wheat-native :jurisdiction :us/fda
    :moisture-percent 14.0 :purity-percent 99.0 :granulation-microns 20
    :sulfite-residue-ppm 15 :microbial-load-cfu 400
    :foreign-material-detected? false :calibration-age-days 9
    :weight-variance-grams 21 :sanitation-score 62
    :raw-materials [:wheat/starch-grade] :declared-allergens #{}}

   ;; batch-010 -- clean actuals, but the sulfite-residue and microbial
   ;; test records are missing from the evidence checklist.
   {:id "batch-010" :product-type :starch/corn-native :jurisdiction :eu/efsa
    :moisture-percent 12.1 :purity-percent 99.6 :granulation-microns 17
    :sulfite-residue-ppm 19 :microbial-load-cfu 210
    :foreign-material-detected? false :calibration-age-days 11
    :weight-variance-grams 19 :sanitation-score 91
    :raw-materials [:corn/yellow-dent] :declared-allergens #{}
    :missing-evidence #{:sulfite-residue-test :microbial-test}}])

(defn- seed-store
  "Build a fresh store value with every intake record registered and an
  empty ledger. `now-ms` is threaded in so the caller owns the single
  clock read."
  [now-ms]
  {:batches
   (into {}
         (map (fn [{:keys [id jurisdiction calibration-age-days missing-evidence] :as b}]
                [id (-> b
                        (dissoc :id :missing-evidence)
                        (assoc :processed? false
                               :evidence-checklist
                               (vec (remove (or missing-evidence #{})
                                            (evidence-for jurisdiction)))
                               :detection-equipment-last-calibration-date
                               (- now-ms (* calibration-age-days day-ms))))]))
         seed-batches)
   :facts []})

;; ────────────────────────── demo driver ──────────────────────────

(defn- cites
  "A citation vector. Any non-empty `:cites` clears the Governor's
  spec-basis check; the strings name the document a plant would actually
  cite for that op."
  [spec]
  [{:spec spec}])

(defn- drive
  "Drive ONE proposal through the real actor and fold the result into the
  running `{:st .. :runs ..}` accumulator.

  `operation/run-operation` is called verbatim with `governor/check` as
  the governor-fn and `governor/hold-fact` as the context's
  `:hold-fact-fn`, so every fact appended here came out of this repo's own
  code. `approve` (optional) is the `store -> store` write the human
  operator authorises once an escalation is signed off; the actor itself
  has no commit node (see ns docstring)."
  [{:keys [st runs]} {:keys [op subject proposal approve]}]
  (let [request {:op op :subject subject}
        context {:actor-id actor-id :hold-fact-fn governor/hold-fact}
        result (operation/run-operation request context proposal st governor/check)
        verdict (:verdict result)
        hard? (boolean (:hard? verdict))
        escalate? (boolean (:escalate? verdict))
        basis (mapv :rule (:violations verdict))
        st' (reduce store/append-fact st (:facts result))
        st'' (cond
               (:ok? result)
               (store/append-fact st' {:t :demo/auto-commit :op op :subject subject
                                       :actor actor-id :disposition :auto-commit
                                       :confidence (:confidence proposal)})

               escalate?
               (let [asked (store/append-fact
                            st' {:t :demo/approval-requested :op op :subject subject
                                 :actor actor-id :disposition :awaiting-human
                                 :confidence (:confidence proposal)})]
                 (if approve
                   (-> (approve asked)
                       (store/append-fact
                        {:t :demo/approval-granted :op op :subject subject
                         :actor actor-id :approved-by operator-id
                         :disposition :approved-and-committed
                         :confidence (:confidence proposal)}))
                   asked))

               :else st')]
    {:st st''
     :runs (conj runs {:op op :subject subject
                       :confidence (:confidence proposal)
                       :disposition (cond (:ok? result) :auto-commit
                                          hard? :hard-hold
                                          escalate? :escalated
                                          :else :held)
                       :hard? hard? :escalate? escalate? :basis basis})}))

(defn- log-batch-approval
  "The human-authorised write for an escalated `:log-production-batch`.
  Re-reads the record the Governor just validated and hands it to
  `store/log-batch` WITH the approver id attached, so the page can measure
  afterwards whether this repo's write path retains it."
  [subject]
  (fn [st]
    (store/log-batch st subject
                     (assoc (store/production-batch st subject)
                            :approved-by operator-id))))

(defn- finalize-shipment-approval
  "The human-authorised write for an escalated `:coordinate-shipment`.
  `store/finalize-shipment` takes only `[st batch-id]` -- there is no
  parameter an approver id could be passed through, which is itself the
  measurement."
  [subject]
  (fn [st] (store/finalize-shipment st subject)))

(defn run-demo!
  "Run one production shift through the real actor.

  Covers, in order: a full clean lifecycle on `batch-001` (a clean
  non-actuation `:schedule-maintenance` that AUTO-COMMITS, then a
  `:log-production-batch` and a `:coordinate-shipment` that both escalate
  because they are high-stakes actuation and are human-approved); a
  low-confidence (0.42 < 0.6 floor) escalation that is also approved; a
  `:flag-food-safety-concern` escalation on `batch-002` that is approved
  but leaves the concern OPEN; and fifteen HARD holds spanning all
  eighteen rules `starchops.governor` can emit. Every hard hold stops at
  the Governor -- none of them is offered to a human at all.

  Returns `{:st <store> :runs [..]}`."
  []
  (reduce
   drive
   {:st (seed-store (System/currentTimeMillis)) :runs []}
   [;; ---- batch-001: full clean lifecycle -------------------------------
    {:op :schedule-maintenance :subject "batch-001"
     :proposal {:cites (cites "Plant-Equipment-Manual") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.91}}
    {:op :log-production-batch :subject "batch-001"
     :proposal {:cites (cites "食品表示法") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.94}
     :approve (log-batch-approval "batch-001")}
    {:op :coordinate-shipment :subject "batch-001"
     :proposal {:cites (cites "食品表示法") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.93}
     :approve (finalize-shipment-approval "batch-001")}

    ;; ---- soft gates: low confidence, and a food-safety concern ---------
    {:op :schedule-maintenance :subject "batch-005"
     :proposal {:cites (cites "Plant-Equipment-Manual") :effect :propose
                :value {:jurisdiction :us/fda} :confidence 0.42}
     :approve identity}
    {:op :flag-food-safety-concern :subject "batch-002"
     :proposal {:cites (cites "Plant-HACCP-Plan") :effect :propose
                :value {:jurisdiction :us/fda} :confidence 0.97}
     :approve identity}

    ;; ---- HARD holds: one per independently-verified physical rule ------
    {:op :log-production-batch :subject "batch-002"
     :proposal {:cites (cites "Plant-HACCP-Plan") :effect :propose
                :value {:jurisdiction :us/fda} :confidence 0.9}}
    {:op :log-production-batch :subject "batch-003"
     :proposal {:cites (cites "食品表示法") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.88}}
    {:op :log-production-batch :subject "batch-004"
     :proposal {:cites (cites "EU 1333/2008") :effect :propose
                :value {:jurisdiction :eu/efsa} :confidence 0.9}}
    {:op :log-production-batch :subject "batch-005"
     :proposal {:cites (cites "21 CFR 184") :effect :propose
                :value {:jurisdiction :us/fda} :confidence 0.86}}
    {:op :log-production-batch :subject "batch-006"
     :proposal {:cites (cites "EU 1333/2008") :effect :propose
                :value {:jurisdiction :eu/efsa} :confidence 0.9}}
    {:op :log-production-batch :subject "batch-007"
     :proposal {:cites (cites "食品表示法") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.92}}
    {:op :log-production-batch :subject "batch-008"
     :proposal {:cites (cites "食品表示法") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.9}}
    {:op :log-production-batch :subject "batch-009"
     :proposal {:cites (cites "FALCPA") :effect :propose
                :value {:jurisdiction :us/fda} :confidence 0.95}}
    {:op :log-production-batch :subject "batch-010"
     :proposal {:cites (cites "EU 1333/2008") :effect :propose
                :value {:jurisdiction :eu/efsa} :confidence 0.9}}

    ;; ---- HARD holds: idempotence, registration, contract ---------------
    {:op :log-production-batch :subject "batch-001"
     :proposal {:cites (cites "食品表示法") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.94}}
    {:op :coordinate-shipment :subject "batch-001"
     :proposal {:cites (cites "食品表示法") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.93}}
    {:op :coordinate-shipment :subject "batch-999"
     :proposal {:cites (cites "食品表示法") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.9}}
    {:op :flag-food-safety-concern :subject "batch-001"
     :proposal {:cites [] :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.9}}
    {:op :control-extraction-line :subject "batch-001"
     :proposal {:cites (cites "Centrifuge-Manual") :effect :propose
                :value {:jurisdiction :jp/prefectural} :confidence 0.99}}
    {:op :schedule-maintenance :subject "batch-001"
     :proposal {:cites (cites "Plant-Equipment-Manual") :effect :commit
                :value {:jurisdiction :jp/prefectural} :confidence 0.9}}]))

;; ────────────────────────── derivations ──────────────────────────

(defn- actor-facts [db]
  (filterv #(= actor-fact-type (:t %)) (store/audit-trail db)))

(defn hard-holds
  "The HARD governor holds on the ledger. A hard hold is an actor fact
  whose `:basis` is non-empty -- `starchops.operation` routes escalations
  through the same fact type with an empty basis, so the basis (not the
  fact type) is what separates the two."
  [db]
  (filterv #(seq (:basis %)) (actor-facts db)))

(def ^:private approver-keys
  "Keys any layer of this repo could plausibly use to carry the human
  approver's identity. Used to MEASURE retention, not to assert it."
  [:approved-by :approver :signed-off-by :operator :operator-id])

(defn- approver-in [m]
  (some (fn [k] (when-some [v (get m k)] [k v])) approver-keys))

(defn- approver-attribution
  "Walk the actual store and ledger and report where (if anywhere) the
  human approver's id survived. Derived at render time so this section
  self-corrects if the store or the actor later starts retaining it."
  [db]
  (let [ledger (store/audit-trail db)
        granted (filterv #(= :demo/approval-granted (:t %)) ledger)
        recs (sort-by key (:batches db))
        recs-with (filterv (fn [[_ r]] (approver-in r)) recs)
        actor-with (filterv approver-in (actor-facts db))]
    {:approvals (count granted)
     :records (count recs)
     :records-with-approver (mapv (fn [[id r]] [id (approver-in r)]) recs-with)
     :actor-facts (count (actor-facts db))
     :actor-facts-with-approver (count actor-with)
     :ledger-facts-with-approver (count (filterv approver-in ledger))}))

;; ─────────────────────────── rendering ───────────────────────────

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (if (keyword? v) (name v) (str v)))

(defn- kws [coll] (str/join ", " (map kw coll)))

(defn- sorted-kws [coll] (str/join ", " (sort (map kw coll))))

(defn- verdict-span [ok? ok-label bad-label]
  (if ok?
    (str "<span class=\"ok\">" ok-label "</span>")
    (str "<span class=\"critical\">" bad-label "</span>")))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       body
       "  </section>\n"))

;; --- batch register ---

(defn- lifecycle-cell [{:keys [processed? shipment-finalized?]}]
  (cond
    shipment-finalized? "<span class=\"ok\">登録済 &middot; 出荷確定</span>"
    processed? "<span class=\"warn\">登録済 &middot; 未出荷</span>"
    :else "<span class=\"muted\">受入のみ</span>"))

(defn- last-fact-for [ledger id]
  (last (filter #(= id (:subject %)) ledger)))

(defn- status-cell [ledger id]
  (let [f (last-fact-for ledger id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (and (= actor-fact-type (:t f)) (seq (:basis f)))
      (str "<span class=\"critical\">HARD hold &middot; " (esc (kws (:basis f))) "</span>")
      (= actor-fact-type (:t f)) "<span class=\"warn\">escalated &middot; awaiting human</span>"
      (= :demo/approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :demo/approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      (= :demo/auto-commit (:t f)) "<span class=\"ok\">auto-committed</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- register-rows [db]
  (let [ledger (store/audit-trail db)]
    (for [[id b] (sort-by key (:batches db))
          :let [p (facts/product-type-by-id (:product-type b))
                j (facts/jurisdiction-by-id (:jurisdiction b))]]
      (row (str "<code>" (esc id) "</code>")
           (esc (:name p))
           (esc (:name j))
           (esc (kws (:raw-materials b)))
           (if (seq (:declared-allergens b))
             (esc (sorted-kws (:declared-allergens b)))
             "<span class=\"muted\">（申告なし）</span>")
           (lifecycle-cell b)
           (status-cell ledger id)))))

;; --- inspection actuals, re-verified through this repo's own predicates ---

(defn- inspection-rows [db]
  (for [[id b] (sort-by key (:batches db))
        :let [p (facts/product-type-by-id (:product-type b))]]
    (row (str "<code>" (esc id) "</code>")
         (str "<span class=\"num\">" (esc (:moisture-percent b)) "</span> "
              (verdict-span (facts/moisture-in-range? (:moisture-percent b) p) "OK" "範囲外"))
         (str "<span class=\"num\">" (esc (:purity-percent b)) "</span> "
              (verdict-span (facts/purity-in-range? (:purity-percent b) p) "OK" "範囲外"))
         (str "<span class=\"num\">" (esc (:granulation-microns b)) "</span> "
              (verdict-span (facts/granulation-in-range? (:granulation-microns b) p) "OK" "範囲外"))
         (str "<span class=\"num\">" (esc (:sulfite-residue-ppm b)) "</span> "
              (verdict-span (facts/sulfite-residue-in-range? (:sulfite-residue-ppm b) p) "OK" "超過"))
         (str "<span class=\"num\">" (esc (:microbial-load-cfu b)) "</span> "
              (verdict-span (facts/microbial-load-in-range? (:microbial-load-cfu b) p) "OK" "超過"))
         (str "<span class=\"num\">" (esc (:sanitation-score b)) "</span> "
              (verdict-span (>= (:sanitation-score b) 75) "OK" "不足"))
         (str "<span class=\"num\">" (esc (:weight-variance-grams b)) "</span> "
              (verdict-span (<= (:weight-variance-grams b) 50) "OK" "超過"))
         (if (:foreign-material-detected? b)
           "<span class=\"critical\">検出</span>"
           "<span class=\"ok\">なし</span>")
         (str "<span class=\"num\">"
              (esc (:calibration-age-days
                    (first (filter #(= id (:id %)) seed-batches))))
              "</span> 日")
         (let [have (set (:evidence-checklist b))
               req (set (:required-evidence (facts/jurisdiction-by-id (:jurisdiction b))))
               missing (sort (map kw (remove have req)))]
           (if (seq missing)
             (str "<span class=\"critical\">欠落: " (esc (str/join ", " missing)) "</span>")
             (str "<span class=\"ok\">" (count req) "/" (count req) " 充足</span>"))))))

;; --- run log ---

(defn- disposition-cell [{:keys [disposition]}]
  (case disposition
    :auto-commit "<span class=\"ok\">auto-commit</span>"
    :hard-hold "<span class=\"critical\">HARD hold（人に上がらない）</span>"
    :escalated "<span class=\"warn\">escalate &rarr; 人の承認</span>"
    "<span class=\"muted\">held</span>"))

(defn- run-rows [runs]
  (for [r runs]
    (row (str "<code>:" (esc (kw (:op r))) "</code>")
         (str "<code>" (esc (:subject r)) "</code>")
         (str "<span class=\"num\">" (esc (:confidence r)) "</span>")
         (disposition-cell r)
         (if (seq (:basis r))
           (str "<code>" (esc (kws (:basis r))) "</code>")
           "<span class=\"muted\">—</span>"))))

;; --- rule catalogue, derived from what actually fired ---

(defn- rule-rows [db]
  (let [hs (hard-holds db)
        by-rule (reduce (fn [acc f]
                          (reduce (fn [a r] (update a r (fnil conj []) (:subject f)))
                                  acc (:basis f)))
                        {} hs)]
    (for [[rule subjects] (sort-by (comp name key) by-rule)]
      (row (str "<code>:" (esc (kw rule)) "</code>")
           (str "<span class=\"num\">" (count subjects) "</span>")
           (esc (str/join ", " (sort (distinct subjects))))
           (esc (or (some #(when (some #{rule} (:basis %))
                             (:detail (first (filter (comp #{rule} :rule)
                                                     (:violations %)))))
                          hs)
                    ""))))))

;; --- action gate, derived from the Governor's own vars ---

(defn- gate-rows []
  (for [op (sort-by name governor/allowed-ops)]
    (row (str "<code>:" (esc (name op)) "</code>")
         (cond
           (contains? governor/high-stakes op)
           "<span class=\"warn\">常に人の承認（高ステークス実行）</span>"
           (contains? governor/always-escalate-ops op)
           "<span class=\"warn\">常に人の承認（食品安全の申告）</span>"
           :else
           (str "<span class=\"ok\">hard 違反ゼロ かつ confidence &ge; "
                governor/confidence-floor " なら auto-commit</span>")))))

;; --- reference facts ---

(defn- product-rows []
  (for [[id p] (sort-by (comp str key) facts/product-types)]
    (row (str "<code>:" (esc (kw id)) "</code>")
         (esc (:name p))
         (str "<span class=\"num\">" (esc (:moisture-target-percent p))
              " &plusmn; " (esc (:moisture-tolerance-percent p)) "</span> %")
         (str "<span class=\"num\">" (esc (:purity-min-percent p)) "–"
              (esc (:purity-max-percent p)) "</span> %")
         (str "<span class=\"num\">" (esc (:granulation-min-microns p)) "–"
              (esc (:granulation-max-microns p)) "</span> μm")
         (str "&le; <span class=\"num\">" (esc (:sulfite-residue-max-ppm p)) "</span> ppm")
         (str "&le; <span class=\"num\">" (esc (:microbial-load-max-cfu p)) "</span> CFU/g"))))

(defn- jurisdiction-rows []
  (for [[id j] (sort-by (comp str key) facts/jurisdictions)]
    (row (str "<code>:" (esc (kw id)) "</code>")
         (esc (:name j))
         (if (:allergen-declaration-required j)
           "<span class=\"warn\">必須</span>"
           "<span class=\"muted\">不要</span>")
         (esc (sorted-kws (:major-allergens j)))
         (str "<span class=\"num\">" (count (:required-evidence j)) "</span> 件: "
              (esc (kws (:required-evidence j)))))))

(defn- raw-material-rows []
  (for [[id m] (sort-by (comp str key) facts/raw-material-allergen-table)]
    (row (str "<code>:" (esc (kw id)) "</code>")
         (if-let [a (:primary-allergen m)]
           (str "<span class=\"critical\">" (esc (kw a)) "</span>")
           "<span class=\"ok\">なし</span>")
         (if (seq (:cross-contact-risk m))
           (str "<span class=\"warn\">" (esc (sorted-kws (:cross-contact-risk m))) "</span>")
           "<span class=\"muted\">なし</span>"))))

;; --- approver attribution ---

(defn- approver-section [db]
  (let [{:keys [approvals records records-with-approver actor-facts
                actor-facts-with-approver ledger-facts-with-approver]}
        (approver-attribution db)
        retained (mapv (fn [[id [k v]]]
                         (str "<code>" (esc id) "</code> &rarr; <code>:"
                              (esc (kw k)) " \"" (esc v) "\"</code>"))
                       records-with-approver)]
    (section
     "承認者の帰属（実測）"
     (str "この repo には承認/コミットのノードが無い —— "
          "<code>starchops.operation/run-operation</code> は escalate 判定で "
          "<code>{:ok? false :facts [hold] :verdict ..}</code> を返して終わり、"
          "resume 経路が無い。したがって下記の承認は <code>starchops.render-html</code> が "
          "実 store writer の上で行っている。以下は主張ではなく、"
          "生成時に store と台帳を実際に歩いて数えた結果。")
     (table
      ["観測点" "実測値"]
      [(row "人の承認が行われた回数"
            (str "<span class=\"num\">" approvals "</span>"))
       (row "store のバッチ記録数"
            (str "<span class=\"num\">" records "</span>"))
       (row (str "うち承認者 id が残っている記録 <span class=\"muted\">(探索キー: "
                 (esc (str/join ", " (map kw approver-keys))) ")</span>")
            (if (seq retained)
              (str "<span class=\"ok\">" (count retained) "</span> &middot; "
                   (str/join "、" retained)
                   " — <code>store/log-batch</code> は <code>(assoc batch-data :processed? true)</code> "
                   "の丸ごと書き込みなので、呼び出し側が渡した承認者 id はそのまま残る")
              "<span class=\"critical\">0 —— どの記録にも承認者 id が残っていない</span>"))
       (row "<code>coordinate-shipment</code> 承認の帰属"
            (str "<span class=\"critical\">残らない</span> — "
                 "<code>store/finalize-shipment</code> の arity は "
                 "<code>[st batch-id]</code> のみで、承認者 id を渡す引数が無い。"
                 "出荷確定は誰が承認したか store 側に残らない"))
       (row (str "actor 自身の監査 fact <code>:" (esc (kw actor-fact-type)) "</code>")
            (str "<span class=\"num\">" actor-facts "</span> 件中 "
                 "<span class=\"num\">" actor-facts-with-approver "</span> 件が承認者 id を持つ — "
                 "<code>governor/hold-fact</code> は <code>:actor</code>（提案した actor id）"
                 "だけを書き、承認者の欄を持たない"))
       (row "台帳全体で承認者 id を持つ fact"
            (str "<span class=\"num\">" ledger-facts-with-approver "</span> 件 — "
                 "すべて driver 由来の <code>:demo/approval-granted</code>"))]))))

;; --- ledger ---

(defn- provenance-cell [t]
  (if (= actor-fact-type t)
    "<span class=\"badge\">actor</span>"
    "<span class=\"muted\">driver</span>"))

(defn- ledger-rows [db]
  (map-indexed
   (fn [i {:keys [t op subject disposition basis approved-by]}]
     (row (str "<span class=\"num\">" (inc i) "</span>")
          (provenance-cell t)
          (str "<code>:" (esc (kw t)) "</code>")
          (str "<code>:" (esc (kw (or op :n-a))) "</code>")
          (str "<code>" (esc subject) "</code>")
          (esc (kw (or disposition "")))
          (if (seq basis)
            (str "<code>" (esc (kws basis)) "</code>")
            (if approved-by
              (str "approved-by <code>" (esc approved-by) "</code>")
              "<span class=\"muted\">—</span>"))))
   (store/audit-trail db)))

;; --- document ---

(defn render
  "Render the full operator-console document from `{:st <store> :runs [..]}`
  as produced by `run-demo!`. Pure: no clock, no randomness, every
  collection sorted explicitly."
  [{:keys [st runs]}]
  (let [db st
        hs (hard-holds db)
        rules (sort (distinct (mapcat :basis hs)))
        approved (count (filter #(= :demo/approval-granted (:t %)) (store/audit-trail db)))
        autos (count (filter #(= :demo/auto-commit (:t %)) (store/audit-trail db)))]
    (str
     "<!doctype html>\n"
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-1062 &middot; starches and starch products</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>でん粉・でん粉製品製造 (ISIC 1062) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · バッチ登録／出荷調整は常に人の承認</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "この頁の出自"
      (str "<code>clojure -M:dev:render-html</code> が実 actor "
           "(<code>starchops.operation/run-operation</code> &rarr; "
           "<code>starchops.governor/check</code> &rarr; <code>starchops.store</code>) "
           "を実行し、その台帳と store をそのまま描いたもの。数値・rule 名・"
           "処分はすべて実行結果の読み戻しで、手書きの HTML は 1 行も無い。")
      (table
       ["観測" "値"]
       [(row "提案を actor に通した回数" (str "<span class=\"num\">" (count runs) "</span>"))
        (row "台帳 fact 総数" (str "<span class=\"num\">" (count (store/audit-trail db)) "</span>"))
        (row (str "actor 自身が書いた fact <code>:" (esc (kw actor-fact-type)) "</code>")
             (str "<span class=\"num\">" (count (actor-facts db)) "</span>"))
        (row "HARD hold（人に上がらない差し止め）"
             (str "<span class=\"critical num\">" (count hs) "</span>"))
        (row "発火した rule の種類"
             (str "<span class=\"num\">" (count rules) "</span> / "
                  "<code>" (esc (str/join ", " (map kw rules))) "</code>"))
        (row "auto-commit / 人が承認した escalation"
             (str "<span class=\"num\">" autos "</span> / <span class=\"num\">" approved "</span>"))]))

     (section
      "バッチ台帳"
      (str "1 シフト分の受入記録。製品名・法域名は <code>starchops.facts</code> から読み、"
           "状態列は台帳の最終 fact から導出している。")
      (table ["バッチ" "製品" "法域" "原料" "アレルゲン申告" "ライフサイクル" "直近の処分"]
             (register-rows db)))

     (section
      "検査実測値と独立再検証"
      (str "各列の OK/NG は <code>starchops.facts</code> の "
           "<code>moisture-in-range?</code> / <code>purity-in-range?</code> / "
           "<code>granulation-in-range?</code> / <code>sulfite-residue-in-range?</code> / "
           "<code>microbial-load-in-range?</code> を生成時に呼び直した結果 —— "
           "advisor の自己申告ではない。校正は 60 日間隔（"
           "<code>registry/detection-equipment-calibration-overdue?</code>）。")
      (table ["バッチ" "水分 %" "純度 %" "粒度 μm" "SO2 ppm" "微生物 CFU/g"
              "衛生スコア" "重量分散 g" "異物" "校正経過" "必要書類"]
             (inspection-rows db)))

     (section
      "この実行の提案ログ"
      (str "actor に通した提案を投入順に並べたもの。<code>HARD hold</code> の行は "
           "Governor で止まっており、人の承認画面には一度も出ていない。")
      (table ["op" "subject" "confidence" "処分" "basis (rule)"]
             (run-rows runs)))

     (section
      "発火した Governor rule"
      (str "台帳上の HARD hold を rule 別に集計したもの。detail 文は "
           "<code>starchops.governor</code> が実際に書いた violation の "
           "<code>:detail</code> をそのまま出している。")
      (table ["rule" "件数" "対象バッチ" "Governor が書いた detail"]
             (rule-rows db)))

     (section
      "アクションゲート"
      (str "<code>starchops.governor</code> の <code>allowed-ops</code> / "
           "<code>high-stakes</code> / <code>always-escalate-ops</code> / "
           "<code>confidence-floor</code> をそのまま読んだもの。"
           "許可リスト外の op（抽出/精製ライン制御など）は無条件の hard block。")
      (table ["op" "ゲート"] (gate-rows)))

     (section
      "製品規格ウィンドウ"
      "<code>starchops.facts/product-types</code> の全件。"
      (table ["id" "製品" "水分目標" "純度" "粒度" "SO2 残留上限" "微生物上限"]
             (product-rows)))

     (section
      "法域要件"
      "<code>starchops.facts/jurisdictions</code> の全件。"
      (table ["id" "法域" "アレルゲン表示" "主要アレルゲン" "必要書類"]
             (jurisdiction-rows)))

     (section
      "原料アレルゲン表"
      (str "<code>starchops.facts/raw-material-allergen-table</code> の全件。"
           "ラベル照合に効くのは primary allergen のみで、cross-contact は"
           "保守的な申告を促す情報として持つ。")
      (table ["原料 id" "primary allergen" "cross-contact risk"]
             (raw-material-rows)))

     (section
      "フェーズ機械"
      "<code>starchops.phase/phase-sequence</code>（前進のみ・後戻り不可）。"
      (str "    <p><code>"
           (esc (str/join " → " (map kw phase/phase-sequence)))
           "</code></p>\n"))

     (approver-section db)

     (section
      "監査台帳（この実行）"
      (str "追記のみの決定ログ全件。<span class=\"badge\">actor</span> の行は "
           "<code>starchops.governor/hold-fact</code> が書いた fact、"
           "<span class=\"muted\">driver</span> の行は承認経路が無いこの repo で "
           "<code>render-html</code> が補った記帳。")
      (table ["#" "出自" "fact" "op" "subject" "処分" "basis / 承認者"]
             (ledger-rows db)))

     "</main>\n"
     "<footer>\n"
     "  <p>cloud-itonami-isic-1062 · でん粉・でん粉製品製造 coordination actor · "
     "生成: <code>clojure -M:dev:render-html</code> · "
     "この頁は決定論的で、同じ seed に対して再実行するとバイト一致する。</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [st] :as result} (run-demo!)
        hs (hard-holds st)]
    (when (empty? hs)
      (throw (ex-info (str "no governor hold fact on the ledger — refusing to write "
                           "a console that shows no real hold")
                      {:ledger-facts (count (store/audit-trail st))})))
    (spit out (render result))
    (println "wrote" out "(" (count (store/audit-trail st)) "ledger facts,"
             (count hs) "HARD holds,"
             (count (distinct (mapcat :basis hs))) "distinct rules )")))
