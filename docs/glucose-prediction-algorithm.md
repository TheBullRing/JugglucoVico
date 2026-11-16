# Algoritmo de predicción de glucosa a 20 minutos

> **Ficheros afectados:**
> - [`Juggluco-Portrait/Common/src/mobile/java/tk/glucodata/chart/GlucoseChartScreen.kt`](../Juggluco-Portrait/Common/src/mobile/java/tk/glucodata/chart/GlucoseChartScreen.kt) — panel de estadísticas (StatsPanel)
> - [`Juggluco-Portrait/Common/src/mobile/java/tk/glucodata/chart/GlucosePortraitChart.kt`](../Juggluco-Portrait/Common/src/mobile/java/tk/glucodata/chart/GlucosePortraitChart.kt) — canvas portrait (cono de incertidumbre)

---

## 1. Objetivo

Mostrar al usuario una estimación del valor de glucosa que tendrá en 20 minutos,
acompañada de una flecha de tendencia (↗ / → / ↙) y un cono de incertidumbre
visual en el gráfico portrait. El valor se presenta como un chip de color en el
panel de estadísticas (junto a TIR y CV%).

La estimación solo se muestra cuando el último dato del sensor tiene menos de
10 minutos de antigüedad (el sensor sigue activo y emitiendo).

---

## 2. Fuentes de datos de tasa de cambio

### 2.1 Tasa de cambio nativa del sensor (TR nativa) — fuente preferida

**JNI:** `Natives.lastglucose()` → `strGlucose.rate` (float, mg/dL/min)

La función C++ `lastglucose()` (en `watch/watchvalue.cpp`) devuelve la tasa de
cambio calculada por la capa nativa a partir del campo `ch` del último `ScanData`
válido del sensor activo:

```cpp
// watch/watchvalue.cpp:158
const float rateofchange =
    (nonconvert < hist->getminmgdL() || nonconvert > hist->getmaxmgdL())
        ? NAN
        : threshold(poll->ch);
```

- Para sensores **Libre 3 BLE**, `poll->ch` es el `rateOfChange` enviado
  directamente por el sensor en cada paquete BLE (resolución ~0,1 mg/dL/min).
- Para sensores **Libre 2 BLE**, `poll->ch` se deriva del `trend` del
  paquete usando `trend2rate()` (`libre3/bluetooth.cpp:136`).
- El valor es `NaN` cuando la glucosa está fuera del rango operativo del sensor
  o cuando no hay lectura reciente.

**Ventajas sobre el cálculo manual:**
- Es la misma tasa que el sensor reporta internamente, sin ruido de muestreo.
- No requiere historial de lecturas: funciona desde la primera lectura tras
  un nuevo escaneado NFC.
- Es la misma fuente que usa la flecha de tendencia original en la UI NanoVG.

### 2.2 Pendiente lineal calculada — fuente de respaldo

Se usa cuando `Natives.lastglucose()` devuelve `null` o `rate == NaN`
(p.ej. sensores sin TR nativa, modo avión, sensor recién iniciado).

**Ventana temporal:** últimos **20 minutos** de lecturas del sensor activo.

```
windowStart = lastEntry.timestampMs - 20 * 60_000 ms
recentPoints = entries donde:
    - sensorIndex == lastEntry.sensorIndex
    - timestampMs >= windowStart
(ordenados por timestamp, mínimo 2 puntos)

dtMin  = (newest.timestampMs - oldest.timestampMs) / 60_000
slope  = (newest.valueMgdL  - oldest.valueMgdL)  / dtMin   [mg/dL/min]
```

**Por qué 20 minutos y no un número fijo de lecturas:**

| Sensor | Frecuencia BLE | `takeLast(6)` anterior | Ventana 20 min |
|--------|---------------|------------------------|----------------|
| Libre 3 BLE | ~1 min | 6 min de datos | ~20 puntos |
| Libre 2 BLE | ~5 min | 30 min de datos | ~4 puntos |
| Libre NFC   | variable | variable | cobertura consistente |

Con `takeLast(6)` el Libre 3 solo usaba 6 minutos, produciendo una pendiente
muy reactiva al ruido de medición. La ventana temporal de 20 minutos da la
misma cobertura independientemente de la frecuencia del sensor.

---

## 3. Cálculo de la predicción

```
predMgdL = clamp(lastEntry.valueMgdL + slope × 20.0, 20, 400)
```

- `slope` es en mg/dL/min (fuente §2.1 o §2.2 según disponibilidad).
- El clamp de 20–400 mg/dL evita valores clínicamente imposibles.
- El valor se convierte a mmol/L si la unidad del usuario es mmol/L.

---

## 4. Flecha de tendencia

La flecha usa la misma fuente de pendiente que la predicción (TR nativa
si está disponible, calculada si no):

| Condición (mg/dL/min) | Flecha | Texto |
|-----------------------|--------|-------|
| slope > +2,0 | ↗↗ | Subiendo rápido |
| slope > +1,0 | ↗  | Subiendo |
| slope < −2,0 | ↙↙ | Bajando rápido |
| slope < −1,0 | ↙  | Bajando |
| −1,0 ≤ slope ≤ +1,0 | → | Estable |

Los umbrales de ±1,0 y ±2,0 mg/dL/min son coherentes con los usados por
Abbott LibreLink y Dexcom Clarity.

---

## 5. Coloreado del chip de predicción

El chip usa el mismo esquema de 5 zonas que el resto del gráfico:

| Zona | Color |
|------|-------|
| predValue < veryLow | 🔴 rojo `#E74C3C` |
| veryLow ≤ predValue < targetLow | 🟠 naranja `#F39C12` |
| targetLow ≤ predValue ≤ targetHigh | 🟢 verde `#2ECC71` |
| targetHigh < predValue ≤ veryHigh | 🟠 naranja `#F39C12` |
| predValue > veryHigh | 🔴 rojo `#E74C3C` |

`veryLow` y `veryHigh` se obtienen de las alarmas del usuario
(`Natives.alarmverylow()` / `Natives.alarmveryhigh()`).

---

## 6. GMI (Glucose Management Indicator)

### Fórmula

```
GMI (%) = 3,31 + 0,02392 × media_glucosa_mgdL       (ADA 2018)
```

### Requisitos para mostrar el valor

El GMI **no se muestra** salvo que se cumplan ambas condiciones:

1. **≥ 14 días de historial total** en `entries` (span temporal entre la
   lectura más antigua y la más reciente ≥ 14 × 24 × 3 600 000 ms).
2. **≥ 20 lecturas en las últimas 24 horas** (para que la media sea
   representativa del día en curso).

Con menos de 14 días el chip GMI aparece vacío (Spacer). El CV% sí se
muestra desde las primeras 24 h (requisito: ≥ 20 lecturas).

### Justificación del umbral de 14 días

La fórmula GMI fue validada por la ADA sobre estudios de 14 días continuos.
Con menos tiempo:
- La media puede no representar el patrón glucémico habitual del paciente.
- Los periodos de hipoglucemia o hiperglucemia puntual distorsionan el
  estimado de forma desproporcionada.
- El valor resultante diferiría del HbA1c de laboratorio en >0,5 puntos
  porcentuales en más del 30 % de los casos.

---

## 7. CV% (Coeficiente de Variación)

```
mean   = media(entries últimas 24h, valueMgdL)
sd     = desviación estándar(entries últimas 24h, valueMgdL)
CV (%) = (sd / mean) × 100
```

**Requisito mínimo:** ≥ 20 lecturas en las últimas 24 h.

**Semáforo de color:**

| CV% | Color | Interpretación |
|-----|-------|----------------|
| ≤ 28 % | 🟢 verde | Variabilidad aceptable (objetivo ADA/EASD) |
| 28–36 % | 🟠 naranja | Variabilidad elevada |
| > 36 % | 🔴 rojo | Variabilidad muy elevada |

---

## 8. Decisiones de diseño registradas

| Decisión | Alternativa descartada | Motivo |
|----------|------------------------|--------|
| Usar TR nativa como fuente primaria | Calcular siempre desde historial | La TR nativa es la que reporta el sensor; el cálculo manual añade ruido de muestreo sin mejorar la precisión |
| Ventana temporal de 20 min para pendiente fallback | `takeLast(N)` fijo | Con Libre 3 BLE (1 lect/min) `takeLast(6)` era solo 6 min de contexto; la ventana temporal produce cobertura consistente entre sensores |
| Requerir ≥ 14 días para GMI | Mostrar siempre con aviso | Un chip de HbA1c estimado con 2 días de datos puede inducir decisiones clínicas basadas en estadísticas inválidas |
| Mantener CV% desde 24 h | Requerir también 14 días | CV% es un indicador de variabilidad diaria; tiene sentido clínico con datos de 24 h |
| Cono de incertidumbre fijo ±18 dp | Cono proporcional a la varianza | Simplicidad; el cono variable requeriría historial de errores de predicción que no está disponible en las primeras sesiones |

---

## 9. Flujo de datos completo

```
C++ native layer
  └─ lastglucose()  →  strGlucose { time, value, sensorid, rate, index }
       │
       ├─ rate (mg/dL/min)  — TR nativa del sensor ─────────────────┐
       │                                                             │
       └─ (si NaN o null)                                           │
            │                                                        ▼
            ▼                                             slope = sensorRate
  NativeGraphDataRepository.snapshotGlucoseEntries()
  → List<GlucoseEntry> (ventana de 20 min del sensor activo)
  → pendiente lineal: slope = Δvalue / Δtime (mg/dL/min)

  ▼ (slope disponible de cualquiera de las dos fuentes)

  predMgdL = clamp(lastValue + slope × 20, 20, 400)
  predDisplay = convertir a unidad del usuario (mg/dL o mmol/L)

  ▼

  StatsPanel (GlucoseChartScreen.kt)
  → chip "Pred. 20min" coloreado por zona + flecha de tendencia

  GlucosePortraitChart.kt (canvas)
  → cono de incertidumbre ±18 dp + línea punteada hacia predValue
  → punto final semitransparente en (predX, predY)
```

---

## 10. Limitaciones conocidas (para el PR)

- La predicción es **lineal**. No captura aceleraciones (p.ej. inicio de bajada
  tras un pico prandial). Una mejora futura podría aplicar una regresión
  ponderada (lecturas más recientes con más peso).
- El cono de incertidumbre es de **ancho fijo** (±18 dp). No refleja la
  variabilidad histórica del sensor ni la incertidumbre real del modelo.
- La TR nativa del Libre 3 puede tener un **retardo de ~5 min** respecto al
  valor real (latencia de la glucosa intersticial). La predicción no compensa
  este retardo.
- La predicción se desactiva cuando los datos tienen **> 10 min de antigüedad**,
  pero no hay indicador visual explícito de "predicción no disponible" más allá
  de la ausencia del chip.
