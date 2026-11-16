/*
 * Puente público para el diálogo Compose "Configurar WearOS" (path Vico).
 *
 * Replica la lógica de Wearos.show() (NanoVG, intacto) sin tocar ningún View.
 * Vive en el paquete tk.glucodata porque Wearos, Applic.sendbluetooth,
 * SensorBluetooth.afterUnpair y MainActivity.unpairer son package-private.
 *
 * Ninguna clase NanoVG importa este fichero.
 */
package tk.glucodata;

import static tk.glucodata.Applic.JUGGLUCOIDENT;
import static tk.glucodata.Log.doLog;
import static tk.glucodata.MessageSender.isGalaxy;

import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class WearosVicoBridge {
    private static final String LOG_ID = "WearosVicoBridge";

    private static final Map<String, Target> asyncNodes = new ConcurrentHashMap<>();

    private WearosVicoBridge() {}

    /** Reloj/par BLE al que se puede configurar. */
    public static final class Target {
        public final String label;
        public final String displayName;
        public final boolean galaxy;

        Target(String label, String displayName, boolean galaxy) {
            this.label = label;
            this.displayName = displayName;
            this.galaxy = galaxy;
        }

        public String title() {
            return displayName.equals(label) ? label : displayName + " - " + label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Target)) return false;
            Target target = (Target) o;
            return galaxy == target.galaxy && Objects.equals(label, target.label) && Objects.equals(displayName, target.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label, displayName, galaxy);
        }
    }

    /** Estado actual de un reloj (equivale a Wearos.remake()). */
    public static final class Status {
        /** Hay conexión sincronizada: se puede elegir sensor directo. */
        public final boolean directAvailable;
        /** true = el sensor está en el reloj. */
        public final boolean sensorOnWatch;
        /** Se puede elegir dónde introducir cantidades. */
        public final boolean numsAvailable;
        /** true = las cantidades se introducen en el reloj. */
        public final boolean numsOnWatch;

        Status(boolean directAvailable, boolean sensorOnWatch, boolean numsAvailable, boolean numsOnWatch) {
            this.directAvailable = directAvailable;
            this.sensorOnWatch = sensorOnWatch;
            this.numsAvailable = numsAvailable;
            this.numsOnWatch = numsOnWatch;
        }
    }

    public static void refreshDevices() {
        try {
            var send = MessageSender.getMessageSender();
            if (send == null && Applic.app != null) {
                MessageSender.initwearos(Applic.app);
                send = MessageSender.getMessageSender();
            }
            if (send != null) {
                send.finddevices();
            }
            if (Applic.app != null) {
                try {
                    Wearable.getNodeClient(Applic.app).getConnectedNodes().addOnSuccessListener(nodes -> {
                        if (nodes != null) {
                            for (Node node : nodes) {
                                asyncNodes.put(node.getId(), new Target(node.getId(), node.getDisplayName(), isGalaxy(node)));
                            }
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    Wearable.getCapabilityClient(Applic.app).getCapability(JUGGLUCOIDENT, CapabilityClient.FILTER_REACHABLE)
                        .addOnSuccessListener(cap -> {
                            if (cap != null && cap.getNodes() != null) {
                                for (Node node : cap.getNodes()) {
                                    asyncNodes.put(node.getId(), new Target(node.getId(), node.getDisplayName(), isGalaxy(node)));
                                }
                            }
                        });
                } catch (Throwable ignored) {}
            }
            Natives.switchSync();
        } catch (Throwable e) {
            Log.stack(LOG_ID, e);
        }
    }

    public static List<Target> targets() {
        var targets = new LinkedHashMap<String, Target>();

        // 1. Nodos desde MessageSender (descubrimiento estándar de Juggluco)
        var send = MessageSender.getMessageSender();
        if (send == null && Applic.app != null) {
            try {
                MessageSender.initwearos(Applic.app);
                send = MessageSender.getMessageSender();
            } catch (Throwable e) {
                Log.stack(LOG_ID, e);
            }
        }
        if (send != null) {
            var nodes = send.getNodes();
            if (nodes != null) {
                for (var node : nodes)
                    targets.put(node.getId(), new Target(node.getId(), node.getDisplayName(), isGalaxy(node)));
            }
        }

        // 2. Nodos descubiertos asíncronamente desde Google Play Services Wearable
        targets.putAll(asyncNodes);

        // 3. Controles directos BLE / Espejos de Wear
        for (var label : BleMirror.wearControlLabels())
            targets.putIfAbsent(label, new Target(label, label, Applic.ALLGALAXY));

        // 4. Servidores espejo configurados en Juggluco marcados como WearOS
        try {
            int count = Natives.backuphostNr();
            for (int i = 0; i < count; i++) {
                String label = Natives.getbackuplabel(i);
                if (label != null && !label.isEmpty() && !Natives.getHostDeactivated(i) && Natives.isWearOS(i)) {
                    targets.putIfAbsent(label, new Target(label, label, Applic.ALLGALAXY));
                }
            }
        } catch (Throwable ignored) {
        }

        return new ArrayList<>(targets.values());
    }

    public static Status status(Target t) {
        int dirval = Natives.directsensorwatch(t.label);
        int numsval = Natives.hasWatchNums(t.label);
        if (dirval == 0 && !Natives.getusebluetooth()) {
            dirval = -1;
            numsval = -1;
        }
        return new Status(dirval >= 0, dirval != 0, numsval >= 0, numsval != 0);
    }

    public static void switchSync() {
        Natives.switchSync();
    }

    /** true si "Predeterminados" necesita confirmación (reloj aún no sincronizado). */
    public static boolean defaultsNeedConfirm(Target t) {
        return Natives.directsensorwatch(t.label) < 0;
    }

    public static void setDefaults(Target t) {
        var sender = MessageSender.getMessageSender();
        if (sender == null) return;
        sender.toDefaults(t.label);
        Natives.setWearosdefaults(t.label, t.galaxy);
        var main = MainActivity.thisone;
        Applic.setbluetooth(main == null ? Applic.app : main, true);
    }

    /**
     * Aplica la elección del usuario (equivale al botón Cerrar de Wearos.show()).
     *
     * @return false si el reloj aún no está sincronizado y no se ha cambiado nada.
     */
    public static boolean apply(android.app.Activity activity, Target t, boolean watchNums, boolean watchDirect) {
        if (!(activity instanceof MainActivity)) return false;
        final MainActivity context = (MainActivity) activity;
        var name = t.label;
        int currentDirect = Natives.directsensorwatch(name);
        if (currentDirect < 0 || Natives.hasWatchNums(name) < 0
                || (currentDirect == 0 && !Natives.getusebluetooth())) {
            Log.i(LOG_ID, "Waiting for Wear mirror synchronization before changing " + name);
            Natives.switchSync();
            return false;
        }
        final boolean wasdirect = currentDirect != 0;
        if (doLog) Log.i(LOG_ID, "watch " + name + " nums " + watchNums + " direct " + watchDirect + " was " + wasdirect);
        byte[] netinfo = Natives.getmynetinfo(name, true, watchDirect ? 1 : -1, t.galaxy, watchNums ? 1 : -1, false);
        Runnable doswitch = () -> Applic.switchbluetooth(name, netinfo, watchDirect);
        if (Natives.hasAidexX()) {
            if (!wasdirect && watchDirect) {
                SensorBluetooth.afterUnpair(context, res -> doswitch.run());
            } else if (wasdirect && !watchDirect) {
                returnSensorToPhone(context, name, netinfo);
            } else {
                doswitch.run();
            }
        } else {
            doswitch.run();
        }
        return true;
    }

    private static void returnSensorToPhone(MainActivity context, String name, byte[] netinfo) {
        var unpair = new UnpairOverlayHost(context, R.string.releasingsensor);
        unpair.postMessage(context.getString(R.string.unpairingwatch));
        context.unpairer = unpair;
        context.doswitch = () -> Applic.setbluetooth(context, true);
        if (!Applic.sendbluetooth(name, netinfo, false)) {
            context.unpairer = null;
            context.doswitch = null;
            unpair.postFinished(context.getString(R.string.failed), false);
            unpair.postCloser();
        }
    }
}
