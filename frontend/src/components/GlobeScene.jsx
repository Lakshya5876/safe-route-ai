import { Canvas, useLoader, useFrame } from "@react-three/fiber";
import { Stars } from "@react-three/drei";
import * as THREE from "three";
import { useRef } from "react";

function Earth() {
  const meshRef = useRef();

  const [colorMap, normalMap, specularMap] = useLoader(
    THREE.TextureLoader,
    [
      "https://raw.githubusercontent.com/mrdoob/three.js/master/examples/textures/planets/earth_atmos_2048.jpg",
      "https://raw.githubusercontent.com/mrdoob/three.js/master/examples/textures/planets/earth_normal_2048.jpg",
      "https://raw.githubusercontent.com/mrdoob/three.js/master/examples/textures/planets/earth_specular_2048.jpg",
    ]
  );

  // slow rotation
  useFrame(() => {
    if (meshRef.current) {
      meshRef.current.rotation.y += 0.006;
    }
  });

  return (
    <mesh ref={meshRef} position={[3.2, 0, 0]}>
      <sphereGeometry args={[2.6, 64, 64]} />

      <meshPhongMaterial
        map={colorMap}
        normalMap={normalMap}
        specularMap={specularMap}
        shininess={30}

        /* 🌊 brighter ocean */
        color="#bfe9ff"
        emissive="#0a2a4f"
        emissiveIntensity={0.25}
      />
    </mesh>
  );
}

export default function GlobeScene() {
  return (
    <Canvas
      camera={{ position: [0, 0, 6] }}
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        width: "100vw",
        height: "100vh",
        zIndex: -1,     // behind UI
        pointerEvents: "none",
        background: "black"
      }}
    >
      {/* LIGHT */}
      <ambientLight intensity={1.3} />
      <directionalLight position={[5, 3, 5]} intensity={2} />
      <pointLight position={[-5, -3, -5]} intensity={1.5} />

      {/* ⭐ FULLSCREEN STARS */}
      <Stars
        radius={100}
        depth={50}
        count={12000}
        factor={4}
        saturation={0}
        fade
        speed={0.3}
      />

      <Earth />
    </Canvas>
  );
}
