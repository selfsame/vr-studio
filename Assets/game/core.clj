(ns game.core
  (use
    arcadia.core
    arcadia.linear
    hard.core
    hard.physics
    hard.seed
    tween.core)
  (require clojure.core.server)
  (import [UnityEngine GL RenderTexture Graphics Rect Debug Color]))

(defonce noise (noise*))

(defn conte! [c]
  (let [o (clone! :compressed-charcoal)]
    (state+ o :color c)
    (material-color! o c) o))

(defn start [o _]
  (clear-cloned!)
  (clone! :sun)
  (clone! :room)
  (clone! :player)
  (clone! :paper)
  (clone! :model)
  (clone! :debug)
  ;(clone! :newpaper)
  (dorun 
    (map-indexed 
      (fn [i c] 
        (let [o (conte! c)]
          (position! o (v3+ (>v3 o) (v3 (* i 0.02) 0 0))))) 
     [(color 0 0 0)
      (color 1 1 1)
      (color 0.5647059 0.227451 0.1372549)
      (color 0.1764706 0.6392157 0.682353)
      (color 1.0 0.8666667 0.2862745)
      (color 0.01960784 0.1490196 0.5686275)])))

(defn debug [s]
  (set! (.text (cmpt (the debug) UnityEngine.TextMesh)) s))

(defn color-texture [c]
  (let [t (UnityEngine.Texture2D. 1 1)]
    (.SetPixel t 0 0 c)
    (.Apply t) t))

(defn clear-texture []
  (let [whitemap (UnityEngine.Texture2D. 1 1)]
    (.SetPixel whitemap 0 0 UnityEngine.Color/white)
    (.Apply whitemap)
    whitemap))

(defn blank-render-texture [w h]
  (let [rt (RenderTexture. w h 32)]
    (UnityEngine.Graphics/Blit (clear-texture) rt)
    rt))

(defn draw-texture [rt t x y]
  (let [w (.width rt)
        h (.height rt)]
   (set! RenderTexture/active rt)
   (GL/PushMatrix)
   (GL/LoadPixelMatrix 0 w h 0)
   (Graphics/DrawTexture (Rect. (- x 15) (- y 9) 15 9) t)
   (GL/PopMatrix)
   (set! RenderTexture/active nil)))

;TODO variable resolution paper

(defn texture-coord [^UnityEngine.GameObject o v]
  (let [dims (v3 0.655875229 0 0.98)
        scale (.localScale (.transform o))
        dims2 (v3 (/ (.x dims) (.x scale)) 0 (/ (.z dims) (.z scale)))
        half (v3* dims2 0.5)
        point (v3+ half v)]
    point
    (v3 (/  (.x point) (.x dims2)) 0 (/  (.z point) (.z dims2)))))

(defn ^Color mix-colors [^Color a ^Color b r]
  (let [a (v3 (.r a) (.g a) (.b a))
        b (v3 (.r b) (.g b) (.b b))
        v (v3+ (v3* a (- 1 r)) (v3* b r))]
    (Color. (.x v) (.y v) (.z v))))

(defn draw-scanline [rt c x y len]
  (let [x (* (- 1 x) 512) 
        y (* (- 1 y) 512)
        w (.width rt)
        h (.height rt)]
   (dotimes [i (int (UnityEngine.Mathf/Abs len))]
    (let [n (noise (v3* (v3 (+ x i) 0.001 y) 0.3))
          n (* (+ n 1.0) 0.5)
          n (* n n)
          x (- (int x) i)
          y (int y)]
      (.SetPixel rt x y (mix-colors (.GetPixel rt x y) c n))))))

(defn mod-step [n step] (- n (mod n step)))

(defn ray-scan [o v]
  ;(debug (str (.InverseTransformPoint (.transform o) v) "\n" (state o :color)))
  (let [height 512.0
        step (/ 10 height)
        v (.InverseTransformPoint (.transform o) v)
        v (v3 (.x v) (.y v) (mod-step (.z v) step))
        rt (state o :rt)
        ablative (state o :ablative)]
    (dotimes [i 160]    
      (let [a (.TransformPoint (.transform o) (v3+ v (v3 -2 0.1 (- (* i step) 1))))
            b (.TransformPoint (.transform o) (v3+ v (v3 2 0.1 (- (* i step) 1))))]
        (if-let [ah (hit a (.TransformDirection (.transform o) (v3 1 0 0)) 20.0 ablative)]
          (when-let [bh (hit b (.TransformDirection (.transform o) (v3 -1 0 0)) 20.0 ablative)]
            (let [aa (texture-coord o (.InverseTransformPoint (.transform o) (.point ah)))
                  bb (texture-coord o (.InverseTransformPoint (.transform o) (.point bh)))
                  len (* 512 (-  (.x bb) (.x aa)))
                  c (state o :color)]
              (draw-scanline rt c (.x aa) (.z aa) len) )))))
    (.Apply rt)))


(defn paper-collide [o c _]
  (state+ o :color (or (state (.gameObject c) :color) (color 0 0 0)))
  (state+ o :collide (.point (aget (.contacts c) 0))))

(defn paper-update [o _]
  (when-let [c (state o :collide)]
    (ray-scan o c)
    (state+ o :collide nil)))

(defn setup-paper [o _]
  (let [rt (UnityEngine.Texture2D. 512 512 UnityEngine.TextureFormat/RGBA32 false)]
    (set! (.filterMode rt) UnityEngine.FilterMode/Point)
    (.SetTexture (.material (cmpt o UnityEngine.Renderer)) "_MainTex" rt)
    (state+ o :rt rt)
    (state+ o :ablative (mask "ablative"))
    (hook+ o :update #'paper-update)
    (hook+ o :on-collision-stay #'paper-collide)))

'(start nil nil)