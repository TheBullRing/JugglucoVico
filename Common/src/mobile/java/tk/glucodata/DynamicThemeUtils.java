package tk.glucodata;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;

import tk.glucodata.settings.AppTheme;

public class DynamicThemeUtils {
private static final String LOG_ID="DynamicThemeUtils";
    public static int resolveAttributeColor(Context ctx, int attrId, int defaultColor) {
        if (attrId == 0) return defaultColor;
        TypedValue val = new TypedValue();
        
        if (ctx.getTheme().resolveAttribute(attrId, val, true)) {
            try {
                if (val.resourceId != 0) {
                    return ctx.getResources().getColor(val.resourceId, ctx.getTheme());
                }
                if (val.type >= TypedValue.TYPE_FIRST_COLOR_INT && val.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return val.data;
                }
            } catch (Exception e) {
                // Fall back
            }
        }
        return defaultColor;
    }

    public static InsetDrawable createDynamicButton(Context ctx, int radiusDp, boolean isOval) {
        String pkg = ctx.getPackageName();
        
        int bgId = ctx.getResources().getIdentifier("colorButtonBackground", "attr", pkg);
        int normId = ctx.getResources().getIdentifier("colorControlNormal", "attr", pkg);
        int highId = ctx.getResources().getIdentifier("colorControlHighlight", "attr", pkg);

        int nativeBtnColor = resolveAttributeColor(ctx, android.R.attr.colorButtonNormal, Color.LTGRAY);
        
        int cBg = resolveAttributeColor(ctx, bgId, nativeBtnColor);
        int cNorm = resolveAttributeColor(ctx, normId, Color.GRAY);
        int cHigh = resolveAttributeColor(ctx, highId, 0x33FFFFFF);

        float density = ctx.getResources().getDisplayMetrics().density;
        int radiusPx = (int) (radiusDp * density);
        int shape = isOval ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE;

        GradientDrawable normShape = new GradientDrawable();
        normShape.setShape(shape);
        normShape.setColor(cBg);
        if (!isOval) normShape.setCornerRadius(radiusPx);

        GradientDrawable disShape = new GradientDrawable();
        disShape.setShape(shape);
        disShape.setColor(cNorm);
        if (!isOval) disShape.setCornerRadius(radiusPx);

        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(new int[]{-android.R.attr.state_enabled}, disShape);
        stateList.addState(new int[]{}, normShape);

        GradientDrawable mask = new GradientDrawable();
        mask.setShape(shape);
        mask.setColor(Color.WHITE);
        if (!isOval) mask.setCornerRadius(radiusPx);

        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(cHigh), stateList, mask);

        int insetH = (int) (4 * density);
        int insetV = (int) (6 * density);
        
        return new InsetDrawable(ripple, insetH, insetV, insetH, insetV);
    }

    public static void applyTheme(View root) {
        int radius = Natives.getradius();
        boolean isOval = Natives.getisOval();
        applyTheme(root, radius, isOval);
       }
    public static void applyTheme(View root,int radius,boolean isOval) {
        traverseAndStyle(root, radius, isOval);
       }

    private static void traverseAndStyle(View view, int radius, boolean isOval) {
        if (view instanceof Button && !(view instanceof CompoundButton)) {
            int pL = view.getPaddingLeft();
            int pT = view.getPaddingTop();
            int pR = view.getPaddingRight();
            int pB = view.getPaddingBottom();

            view.setBackground(createDynamicButton(view.getContext(), radius, isOval));

            view.setPadding(pL, pT, pR, pB);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                traverseAndStyle(group.getChildAt(i), radius, isOval);
            }
        }
    }
/**
 * Returns the three colours needed to build the Compose ColorScheme from the
 * Activity theme (which has the user's custom Juggluco theme applied).
 * Returns int[]{bgColor, primaryColor, onPrimaryColor, onSurfaceColor}.
 */
public static int[] getThemeColors(Context ctx) {
    String pkg = ctx.getPackageName();
    int bgColorInt  = resolveAttributeColor(ctx, android.R.attr.windowBackground,
                          android.graphics.Color.WHITE);
    int btnBgAttrId = ctx.getResources().getIdentifier("colorButtonBackground", "attr", pkg);
    int btnTxAttrId = ctx.getResources().getIdentifier("colorButtonText",       "attr", pkg);
    int btnBgColor  = resolveAttributeColor(ctx, btnBgAttrId,
                          resolveAttributeColor(ctx, android.R.attr.colorAccent,
                              android.graphics.Color.BLUE));
    int btnTxColor  = resolveAttributeColor(ctx, btnTxAttrId, android.graphics.Color.WHITE);
    int onSurface   = resolveAttributeColor(ctx, android.R.attr.textColorPrimary,
                          android.graphics.Color.BLACK);
    return new int[]{bgColorInt, btnBgColor, btnTxColor, onSurface};
}
static void setTheme(Activity act) {
       int theme=Natives.getTheme();
       Log.i(LOG_ID,"getTheme()="+theme);
       // Aplicar siempre el tema, incluyendo el índice 0 (AndroidDefault).
       // Sin esto, cuando getTheme()==0 la Activity queda con el tema base
       // AppCompat (azul genérico) en lugar del tema oscuro por defecto de Juggluco.
       int idx = (theme >= 0 && theme < AppTheme.values().length) ? theme : 0;
       act.setTheme(AppTheme.values()[idx].getStyleResId());
       if(Natives.getisOval()) {
           act.getTheme().applyStyle(R.style.ShapeOverride_Oval, true);
           }
       }
}
