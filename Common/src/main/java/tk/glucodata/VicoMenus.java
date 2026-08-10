/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */
/*                                                                                   */
/*      Copyright (C) 2021 Jaap Korthals Altes <jaapkorthalsaltes@gmail.com>         */
/*                                                                                   */
/*      Juggluco is free software: you can redistribute it and/or modify             */
/*      it under the terms of the GNU General Public License as published            */
/*      by the Free Software Foundation, either version 3 of the License, or         */
/*      (at your option) any later version.                                          */
/*                                                                                   */
/*      Juggluco is distributed in the hope that it will be useful, but              */
/*      WITHOUT ANY WARRANTY; without even the implied warranty of                   */
/*      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         */
/*      See the GNU General Public License for more details.                         */
/*                                                                                   */
/*      You should have received a copy of the GNU General Public License            */
/*      along with Juggluco. If not, see <https://www.gnu.org/licenses/>.            */

package tk.glucodata;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static tk.glucodata.Applic.isWearable;
import static tk.glucodata.Log.doLog;
import static tk.glucodata.MainActivity.REQUEST_BARCODE;
import static tk.glucodata.Natives.getInvertColors;
import static tk.glucodata.Natives.getshowscans;
import static tk.glucodata.Natives.getsystemui;
import static tk.glucodata.Natives.setsystemui;
import static tk.glucodata.NumberView.smallScreen;
import static tk.glucodata.settings.Settings.removeContentView;

import tk.glucodata.settings.Settings;

/**
 * Menu implementation for the Vico/Compose UI path.
 *
 * Differences from the legacy {@link Menus}:
 * <ul>
 *   <li>No {@code Menus.on} flag — the Vico chart does not depend on it.</li>
 *   <li>Every state-change listener calls
 *       {@link NativeGraphDataRepository#notifyDataChanged()} instead of
 *       {@code activity.requestRender()} (which is a no-op in the Vico path).</li>
 *   <li>The same XML layouts are reused: {@code res/layout/menus.xml} (landscape)
 *       and {@code res/layout-port/menus.xml} (portrait, ViewFlipper with 3 tabs).
 *       Android picks the correct variant automatically via the resource qualifier.</li>
 *   <li>In portrait the caller can pass {@code initialTab} (0=Acciones, 1=Datos,
 *       2=Ver) to open directly at the relevant tab — mirrors
 *       {@link Menus#show(MainActivity, int)} for the old UI.</li>
 * </ul>
 */
public class VicoMenus {

    private static final String LOG_ID = "VicoMenus";

    /** Refresh the Vico chart immediately after a native flag changes. */
    private static void refresh() {
        tk.glucodata.chart.NativeGraphDataRepository.notifyDataChanged();
    }

    // ── Public entry-points ───────────────────────────────────────────────────

    /**
     * Open the menu at the given tab index (portrait ViewFlipper only).
     * In landscape the index is ignored — all buttons are visible at once.
     *
     * @param act        The host Activity.
     * @param initialTab 0=Acciones, 1=Datos, 2=Ver
     */
    public static void show(MainActivity act, int initialTab) {
        show(act);
        if (initialTab < 0 || initialTab > 2) return;
        android.view.View decor = act.getWindow().getDecorView();
        android.widget.ViewFlipper flipper = decor.findViewById(R.id.menus_flipper);
        if (flipper != null) flipper.setDisplayedChild(initialTab);
    }

    public static void show(MainActivity act) {
        if (doLog) { Log.i(LOG_ID, "show"); }

        // Inflate using the activity's current configuration so the correct
        // layout qualifier (layout/ vs layout-port/) is chosen automatically.
        android.content.res.Configuration cfg = act.getResources().getConfiguration();
        android.content.Context cfgCtx = new ContextThemeWrapper(
                act.createConfigurationContext(cfg), act.getTheme());
        LayoutInflater inflater = LayoutInflater.from(act).cloneInContext(cfgCtx);
        View view = inflater.inflate(R.layout.menus, null, false);
        view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        // Wrapper absorbs system-bar padding so inner GridLayout/ScrollView
        // measures correctly within the available inset-free bounds.
        FrameLayout wrapper = new FrameLayout(act);
        wrapper.addView(view, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        wrapper.setBackgroundColor(Applic.backgroundcolor);

        view.setAccessibilityDelegate(Layout.accessDeli);
        view.setBackgroundColor(Applic.backgroundcolor);
        act.themeLightBars();

        // Back gesture dismisses the menu — no Menus.on flag needed here.
        act.setonback(() -> {
            act.lightBars(!getInvertColors());
            if (doLog) { Log.i(LOG_ID, "onback"); }
            removeContentView(wrapper);
            // No requestRender() — the Vico chart is live via StateFlow.
        });

        // ── "Ver" section: checkboxes that affect chart overlay visibility ────

        CheckBox systemuiview = view.findViewById(R.id.systemui);
        systemuiview.setChecked(getsystemui());
        systemuiview.setOnCheckedChangeListener((btn, checked) -> {
            setsystemui(checked);
            act.selectionSystemUI();
        });

        CheckBox menusview = view.findViewById(R.id.menus);
        menusview.setChecked(true);
        menusview.setOnCheckedChangeListener((btn, checked) -> {
            act.poponback();
            act.lightBars(!getInvertColors());
            removeContentView(wrapper);
        });

        CheckBox scansview = view.findViewById(R.id.scans);
        scansview.setChecked(getshowscans());
        scansview.setOnCheckedChangeListener((btn, checked) -> {
            Natives.setshowscans(checked);
            refresh();
        });

        CheckBox calibratedscansview = view.findViewById(R.id.calibratedscans);
        calibratedscansview.setChecked(Natives.getshowcalibratedscans());
        calibratedscansview.setOnCheckedChangeListener((btn, checked) -> {
            Natives.setshowcalibratedscans(checked);
            refresh();
        });

        CheckBox streamview = view.findViewById(R.id.stream);
        streamview.setChecked(Natives.getshowstream());
        streamview.setOnCheckedChangeListener((btn, checked) -> {
            Natives.setshowstream(checked);
            refresh();
        });

        CheckBox calibratedstreamview = view.findViewById(R.id.calibratedstream);
        calibratedstreamview.setChecked(Natives.getshowcalibratedstream());
        calibratedstreamview.setOnCheckedChangeListener((btn, checked) -> {
            Natives.setshowcalibratedstream(checked);
            refresh();
        });

        CheckBox historyview = view.findViewById(R.id.history);
        historyview.setChecked(Natives.getshowhistories());
        historyview.setOnCheckedChangeListener((btn, checked) -> {
            Natives.setshowhistories(checked);
            refresh();
        });

        CheckBox calibratedhistoryview = view.findViewById(R.id.calibratedhistory);
        calibratedhistoryview.setChecked(Natives.getshowcalibratedhistories());
        calibratedhistoryview.setOnCheckedChangeListener((btn, checked) -> {
            Natives.setshowcalibratedhistories(checked);
            refresh();
        });

        CheckBox amountsview = view.findViewById(R.id.amounts);
        amountsview.setChecked(Natives.getshownumbers());
        amountsview.setOnCheckedChangeListener((btn, checked) -> {
            Natives.setshownumbers(checked);
            refresh();
        });

        CheckBox mealsview = view.findViewById(R.id.meals);
        mealsview.setChecked(Natives.getshowmeals());
        mealsview.setOnCheckedChangeListener((btn, checked) -> {
            Natives.setshowmeals(checked);
            refresh();
        });

        CheckBox darkmodeview = view.findViewById(R.id.darkmode);
        darkmodeview.setChecked(getInvertColors());
        darkmodeview.setOnCheckedChangeListener((btn, checked) -> {
            Natives.setInvertColors(checked);
            refresh();
        });

        CheckBox glucosefloatview = view.findViewById(R.id.glucosefloat);
        glucosefloatview.setChecked(Natives.getfloatglucose());
        glucosefloatview.setOnCheckedChangeListener((btn, checked) ->
                Floating.setfloatglucose(act, checked));

        // ── "Acciones" section: action buttons ───────────────────────────────

        view.findViewById(R.id.watch).setOnClickListener(v -> {
            if (!isWearable) {
                act.lightBars(!getInvertColors());
                removeContentView(wrapper);
                tk.glucodata.Watch.show(act);
            }
        });

        view.findViewById(R.id.sensor).setOnClickListener(v -> {
            act.lightBars(!getInvertColors());
            removeContentView(wrapper);
            bluediag.start(act);
        });

        view.findViewById(R.id.settings).setOnClickListener(v -> {
            removeContentView(wrapper);
            Settings.set(act);
        });

        Button aboutview = view.findViewById(R.id.about);
        if (tk.glucodata.BuildConfig.SiBionics == 1) {
            aboutview.setText(R.string.photo);
            aboutview.setOnClickListener(v -> PhotoScan.scan(act, REQUEST_BARCODE));
        } else {
            aboutview.setOnClickListener(v -> {
                var c = Applic.app.curve;
                if (c != null) c.doabout(act);
            });
        }

        view.findViewById(R.id.close).setOnClickListener(v ->
                act.moveTaskToBack(true));

        view.findViewById(R.id.export).setOnClickListener(v -> {
            var c = Applic.app.curve;
            if (c != null) {
                act.lightBars(!getInvertColors());
                removeContentView(wrapper);
                c.dialogs.showexport(act, c.getWidth(), c.getHeight(), null);
            }
        });

        view.findViewById(R.id.mirror).setOnClickListener(v -> {
            removeContentView(wrapper);
            new Backup().mkbackupview(act);
        });

        // ── "Datos" section: navigation + entry buttons ───────────────────────

        view.findViewById(R.id.newamount).setOnClickListener(v -> {
            if (Natives.staticnum()) {
                help.help(R.string.staticnum, act);
            } else {
                var c = Applic.app.curve;
                if (c != null) {
                    act.lightBars(!getInvertColors());
                    removeContentView(wrapper);
                    c.numberview.addnumberview(act);
                    if (!smallScreen) c.showkeyboard(act);
                }
            }
        });

        view.findViewById(R.id.list).setOnClickListener(v -> {
            var c = Applic.app.curve;
            if (c != null) {
                act.lightBars(!getInvertColors());
                removeContentView(wrapper);
                Natives.makenumbers();
                refresh();
                c.getnumcontrol(act);
            }
        });

        view.findViewById(R.id.statistics).setOnClickListener(v -> {
            if (Natives.makepercentages()) {
                act.lightBars(!getInvertColors());
                removeContentView(wrapper);
                refresh();
                Stats.mkstats(act);
            }
        });

        view.findViewById(R.id.talk).setOnClickListener(v -> {
            removeContentView(wrapper);
            tk.glucodata.Talker.config(act);
        });

        view.findViewById(R.id.lastscan).setOnClickListener(v -> {
            if (Natives.showlastscan()) {
                act.lightBars(!getInvertColors());
                removeContentView(wrapper);
                refresh();
            }
        });

        view.findViewById(R.id.now).setOnClickListener(v -> {
            act.lightBars(!getInvertColors());
            removeContentView(wrapper);
            Natives.settonow();
            refresh();
        });

        view.findViewById(R.id.search).setOnClickListener(v -> {
            var c = Applic.app.curve;
            if (c != null) {
                act.lightBars(!getInvertColors());
                removeContentView(wrapper);
                c.startsearch();
            }
        });

        view.findViewById(R.id.date).setOnClickListener(v -> {
            var c = Applic.app.curve;
            if (c != null) {
                act.lightBars(!getInvertColors());
                removeContentView(wrapper);
                c.startdatepick(Natives.getstarttime());
            }
        });

        view.findViewById(R.id.dayback).setOnClickListener(v -> {
            act.lightBars(!getInvertColors());
            removeContentView(wrapper);
            Natives.prevday(1);
            refresh();
        });

        view.findViewById(R.id.daylater).setOnClickListener(v -> {
            act.lightBars(!getInvertColors());
            removeContentView(wrapper);
            Natives.nextday(1);
            refresh();
        });

        view.findViewById(R.id.weekback).setOnClickListener(v -> {
            act.lightBars(!getInvertColors());
            removeContentView(wrapper);
            Natives.prevday(7);
            refresh();
        });

        view.findViewById(R.id.weeklater).setOnClickListener(v -> {
            act.lightBars(!getInvertColors());
            removeContentView(wrapper);
            Natives.nextday(7);
            refresh();
        });

        // addMyContentView applies WindowInsets automatically in the Vico/EdgeToEdge path.
        act.addMyContentView(wrapper, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }
}
