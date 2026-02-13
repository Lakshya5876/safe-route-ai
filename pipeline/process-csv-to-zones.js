#!/usr/bin/env node
/**
 * SafeRoute Delhi – CSV to Risk Zones JSON
 * Reads CSV with columns: lat, lng, category, severity (or name, latitude, longitude, radius_km, base_risk, category)
 * Outputs: backend/src/main/resources/delhi-risk-zones.json
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const DEFAULT_INPUT = path.join(__dirname, 'sample-delhi-crimes.csv');
const OUTPUT_PATH = path.join(__dirname, '..', 'backend', 'src', 'main', 'resources', 'delhi-risk-zones.json');

// Weights by category for baseRisk (scale 1–50)
const CATEGORY_WEIGHT = {
  assault: 40,
  theft: 32,
  accident_prone: 28,
  poor_lighting: 25,
  unsafe_pedestrian: 22,
  other: 20,
};

const RADIUS_KM = 0.8; // default radius if not in CSV
const CLUSTER_DISTANCE_KM = 0.5; // merge points within this distance

function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function parseCsv(content) {
  const lines = content.split(/\r?\n/).filter(Boolean);
  if (lines.length < 2) return [];
  const header = lines[0].toLowerCase().split(',').map((h) => h.trim());
  const rows = [];
  for (let i = 1; i < lines.length; i++) {
    const values = lines[i].split(',').map((v) => v.trim());
    const row = {};
    header.forEach((h, j) => (row[h] = values[j] ?? ''));
    rows.push(row);
  }
  return rows;
}

function normalizeRow(row) {
  const lat = parseFloat(row.lat ?? row.latitude);
  const lng = parseFloat(row.lng ?? row.longitude ?? row.lon);
  if (Number.isNaN(lat) || Number.isNaN(lng)) return null;
  if (lat < 28.4 || lat > 28.9 || lng < 76.8 || lng > 77.4) return null; // Delhi bounds

  const category = (row.category || row.type || 'other').toLowerCase().replace(/\s+/g, '_');
  const baseRisk = Number(row.base_risk ?? row.baseRisk ?? row.severity) || CATEGORY_WEIGHT[category] || CATEGORY_WEIGHT.other;
  const radiusKm = Number(row.radius_km ?? row.radius ?? row.radiusKm) || RADIUS_KM;
  const name = row.name || row.area || `${category}_${row.lat}_${row.lng}`;

  return {
    name: String(name).slice(0, 80),
    latitude: Math.round(lat * 1e4) / 1e4,
    longitude: Math.round(lng * 1e4) / 1e4,
    radius: Math.min(2.5, Math.max(0.3, radiusKm)),
    baseRisk: Math.min(50, Math.max(5, baseRisk)),
    category: category in CATEGORY_WEIGHT ? category : 'other',
  };
}

function cluster(zones) {
  const out = [];
  const used = new Set();

  for (let i = 0; i < zones.length; i++) {
    if (used.has(i)) continue;
    const z = zones[i];
    let count = 1;
    let riskSum = z.baseRisk;
    for (let j = i + 1; j < zones.length; j++) {
      if (used.has(j)) continue;
      const z2 = zones[j];
      const d = haversineKm(z.latitude, z.longitude, z2.latitude, z2.longitude);
      if (d <= CLUSTER_DISTANCE_KM) {
        used.add(j);
        count++;
        riskSum += z2.baseRisk;
      }
    }
    out.push({
      name: z.name,
      latitude: z.latitude,
      longitude: z.longitude,
      radius: z.radius,
      baseRisk: Math.min(50, Math.round(riskSum / count) + (count > 1 ? 5 : 0)),
      category: z.category,
    });
  }
  return out;
}

function main() {
  const inputPath = process.argv[2] || DEFAULT_INPUT;
  if (!fs.existsSync(inputPath)) {
    console.error('Input file not found:', inputPath);
    console.error('Usage: node process-csv-to-zones.js [input.csv]');
    process.exit(1);
  }

  const content = fs.readFileSync(inputPath, 'utf8');
  const rows = parseCsv(content);
  const zones = rows.map(normalizeRow).filter(Boolean);
  const clustered = cluster(zones);

  const outDir = path.dirname(OUTPUT_PATH);
  if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(clustered, null, 2), 'utf8');
  console.log('Wrote', clustered.length, 'zones to', OUTPUT_PATH);
}

main();
