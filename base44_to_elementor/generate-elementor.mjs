/**
 * Generates an Elementor Pro page template JSON
 * for the NovaLife Base44 landing page.
 */
import { writeFileSync } from 'fs';
import { randomBytes } from 'crypto';

const uid = () => randomBytes(4).toString('hex');

// ── Color palette ──────────────────────────────────────────────────────────────
const C = {
  teal:       '#00A2B8',
  tealDeep:   '#1A717D',
  tealLight:  '#DBF0F3',
  tealMid:    '#EBF5F7',   // teal-light @ 40% opacity on white
  greenLight: '#E6F3EC',
  greenMid:   '#EDF7F0',   // green-light @ 30% opacity on white
  charcoal:   '#383F51',
  white:      '#FFFFFF',
  bodyBg:     '#F5F5F5',
  footerBg:   '#1A717D',
};

// ── Images ────────────────────────────────────────────────────────────────────
const IMG = {
  logo:    'https://media.base44.com/images/public/69cbc1e08a7336e77fee4a13/692092b15_horizontaltagline2x.png',
  forYou:  'https://media.base44.com/images/public/69cbc1e08a7336e77fee4a13/9e0b36ff5_ForYou.png',
  ovaLife: 'https://media.base44.com/images/public/69cbc1e08a7336e77fee4a13/215799d64_OvaLife.png',
};

// ── Helpers ───────────────────────────────────────────────────────────────────
const section = (settings, ...columns) => ({
  id: uid(), elType: 'section', isInner: false, settings, elements: columns,
});

const innerSection = (settings, ...columns) => ({
  id: uid(), elType: 'section', isInner: true, settings, elements: columns,
});

const column = (size, settings, ...elements) => ({
  id: uid(), elType: 'column', settings: { _column_size: size, ...settings }, elements,
});

const widget = (widgetType, settings) => ({
  id: uid(), elType: 'widget', widgetType, settings, elements: [],
});

// ── Widget factories ──────────────────────────────────────────────────────────
const heading = (title, level, { color = C.charcoal, size, align = 'right', weight = '700' } = {}) =>
  widget('heading', {
    title,
    header_size: level,
    align,
    title_color: color,
    typography_typography: 'custom',
    typography_font_family: 'Heebo',
    typography_font_weight: weight,
    ...(size ? { typography_font_size: { unit: 'px', size } } : {}),
  });

const textEditor = (html, { align = 'right', color = C.charcoal } = {}) =>
  widget('text-editor', {
    editor: html,
    align,
    text_color: color,
    typography_typography: 'custom',
    typography_font_family: 'Heebo',
  });

const image = (url, alt, { align = 'center', width } = {}) =>
  widget('image', {
    image: { url, alt },
    image_size: 'full',
    align,
    ...(width ? { width: { unit: 'px', size: width } } : {}),
  });

const button = (text, url, { bgColor = C.teal, textColor = C.white, align = 'right', borderColor } = {}) =>
  widget('button', {
    text,
    link: { url, is_external: false, nofollow: false },
    align,
    background_color: bgColor,
    button_text_color: textColor,
    border_color: borderColor || bgColor,
    typography_font_family: 'Heebo',
    typography_font_weight: '700',
    border_radius: { unit: 'px', top: 8, right: 8, bottom: 8, left: 8 },
    ...(borderColor ? { background_background: 'classic', background_color: 'transparent', button_border_border: 'solid', button_border_width: { unit: 'px', top: 2, right: 2, bottom: 2, left: 2 }, button_border_color: borderColor } : {}),
  });

const spacer = (height) =>
  widget('spacer', { space: { unit: 'px', size: height } });

const divider = () =>
  widget('divider', { color: { color: C.tealLight } });

// Pill-label badge using heading with custom CSS
const pillLabel = (text, bg = C.teal, textColor = C.white) =>
  widget('heading', {
    title: text,
    header_size: 'p',
    align: 'right',
    title_color: textColor,
    typography_font_family: 'Heebo',
    typography_font_weight: '700',
    typography_font_size: { unit: 'px', size: 12 },
    _css_classes: 'nv-pill-label',
    custom_css: `selector { display: inline-block; background: ${bg}; color: ${textColor}; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 700; letter-spacing: 0.05em; text-transform: uppercase; }`,
  });

// Stat box
const statBox = (number, unit, label) =>
  column(33, { background_background: 'classic', background_color: 'rgba(255,255,255,0.6)', border_radius: { unit: 'px', top: 12, right: 12, bottom: 12, left: 12 }, padding: { unit: 'px', top: 12, right: 12, bottom: 12, left: 12, isLinked: false } },
    widget('heading', {
      title: `<span style="font-size:1.6em;font-weight:900;color:${C.tealDeep}">${number}</span><span style="font-size:0.9em;font-weight:500;color:${C.teal};margin-right:4px"> ${unit}</span>`,
      header_size: 'div',
      align: 'right',
      typography_font_family: 'Heebo',
    }),
    textEditor(`<p style="font-size:12px;color:rgba(56,63,81,0.7);margin-top:4px">${label}</p>`),
  );

// Accordion item row (nutrition/lifestyle)
const accordionItem = (num, numBg, title, subtitle) =>
  innerSection(
    { background_background: 'classic', background_color: C.white, border_radius: { unit: 'px', top: 16, right: 16, bottom: 16, left: 16 }, padding: { unit: 'px', top: 20, right: 20, bottom: 20, left: 20, isLinked: false }, box_shadow_box_shadow_type: 'yes', box_shadow_box_shadow: { horizontal: 0, vertical: 2, blur: 8, spread: 0, color: 'rgba(0,162,184,0.08)' } },
    column(100, {},
      innerSection({ gap: '16px' },
        column(5, { vertical_alignment: 'middle' },
          widget('heading', {
            title: `<span style="display:inline-flex;align-items:center;justify-content:center;width:24px;height:24px;border-radius:50%;background:${numBg};color:${C.tealDeep};font-size:11px;font-weight:900">${num}</span>`,
            header_size: 'div', align: 'center',
          }),
        ),
        column(95, { vertical_alignment: 'middle' },
          widget('heading', { title, header_size: 'h3', align: 'right', title_color: C.tealDeep, typography_font_family: 'Heebo', typography_font_weight: '700', typography_font_size: { unit: 'px', size: 18 } }),
          textEditor(`<p style="font-size:14px;color:rgba(56,63,81,0.6)">${subtitle}</p>`),
        ),
      ),
    ),
  );

// Lifestyle card
const lifestyleCard = (emoji, subtitle, title, description) =>
  innerSection(
    { background_background: 'classic', background_color: C.white, border_radius: { unit: 'px', top: 16, right: 16, bottom: 16, left: 16 }, padding: { unit: 'px', top: 24, right: 24, bottom: 24, left: 24, isLinked: false }, box_shadow_box_shadow_type: 'yes', box_shadow_box_shadow: { horizontal: 0, vertical: 2, blur: 8, spread: 0, color: 'rgba(0,0,0,0.06)' } },
    column(100, {},
      textEditor(`<p style="font-size:32px;text-align:right">${emoji}</p>`),
      widget('heading', { title: subtitle, header_size: 'p', align: 'right', title_color: C.teal, typography_font_family: 'Heebo', typography_font_weight: '700', typography_font_size: { unit: 'px', size: 12 } }),
      widget('heading', { title, header_size: 'h3', align: 'right', title_color: C.tealDeep, typography_font_family: 'Heebo', typography_font_weight: '700', typography_font_size: { unit: 'px', size: 18 } }),
      textEditor(`<p style="font-size:14px;color:${C.charcoal};line-height:1.6">${description}</p>`),
    ),
  );

// Product card
const productCard = (imgUrl, imgAlt, title, desc) =>
  innerSection(
    { background_background: 'classic', background_color: C.tealLight, border_radius: { unit: 'px', top: 20, right: 20, bottom: 20, left: 20 }, padding: { unit: 'px', top: 24, right: 24, bottom: 24, left: 24, isLinked: false } },
    column(100, {},
      image(imgUrl, imgAlt, { align: 'center', width: 180 }),
      spacer(16),
      widget('heading', { title, header_size: 'h3', align: 'right', title_color: C.tealDeep, typography_font_family: 'Heebo', typography_font_weight: '700', typography_font_size: { unit: 'px', size: 16 } }),
      textEditor(`<p style="font-size:14px;color:${C.charcoal};line-height:1.6">${desc}</p>`),
    ),
  );

// ── PAGE CONTENT ───────────────────────────────────────────────────────────────

const content = [

  // ─── SECTION 0: Hero ─────────────────────────────────────────────────────────
  section(
    {
      layout: 'full_width',
      content_width: { unit: 'px', size: 1200 },
      background_background: 'classic',
      background_color: C.tealLight,
      padding: { unit: 'px', top: '80', right: '24', bottom: '80', left: '24', isLinked: false },
      gap: 'extended',
    },
    // Right column – content (order 1 in RTL = displayed first)
    column(50, { vertical_alignment: 'center' },
      image(IMG.logo, 'NovaLife — פוריות האישה והגבר', { align: 'right', width: 137 }),
      spacer(24),
      widget('heading', {
        title: 'מדריך לשיפור<br><span style="color:' + C.teal + '">איכות וכמות</span><br>הביציות',
        header_size: 'h1',
        align: 'right',
        title_color: C.tealDeep,
        typography_typography: 'custom',
        typography_font_family: 'Heebo',
        typography_font_weight: '900',
        typography_font_size: { unit: 'px', size: 56 },
        typography_line_height: { unit: 'em', size: 1.15 },
      }),
      spacer(16),
      textEditor(
        `<p style="font-size:18px;color:${C.charcoal};line-height:1.7">איכות הביציות היא קריטית להצלחת קליטה להריון ולחוויית הריון בריא. שינויים בתזונה, בתוספים ובאורח החיים יכולים להשפיע כבר בחודשים הקרובים.</p>`,
        { align: 'right' },
      ),
      spacer(24),
      innerSection({ gap: '12px', padding: { unit: 'px', top: 0, right: 0, bottom: 0, left: 0, isLinked: true } },
        column(50, {},
          button('קראי את המדריך', 'https://novalife-egg-quality.base44.app/#nutrition', { bgColor: C.teal, textColor: C.white }),
        ),
        column(50, {},
          button('לתוספי התזונה', 'https://novalife-egg-quality.base44.app/#supplements', { bgColor: 'transparent', textColor: C.tealDeep, borderColor: C.teal }),
        ),
      ),
      spacer(32),
      // Stats row
      innerSection(
        { gap: '12px', padding: { unit: 'px', top: 0, right: 0, bottom: 0, left: 0, isLinked: true } },
        statBox('90', 'יום', 'לפני הביוץ מתחיל התהליך'),
        statBox('3', 'חלקים', 'תזונה, תוספים, אורח חיים'),
        statBox('100%', '', 'ידע ישים ביומיום'),
      ),
    ),
    // Left column – illustration placeholder
    column(50, { vertical_alignment: 'middle' },
      textEditor(
        `<div style="text-align:center;padding:40px;background:rgba(219,240,243,0.5);border-radius:50%;max-width:320px;margin:0 auto">
          <p style="font-size:120px;line-height:1">🥚</p>
          <p style="font-size:14px;color:${C.tealDeep};font-weight:600;font-family:Heebo,sans-serif">הכניסי כאן את האיור המקורי</p>
        </div>`,
      ),
    ),
  ),

  // ─── SECTION 1: Why – Introduction ───────────────────────────────────────────
  section(
    {
      layout: 'full_width',
      content_width: { unit: 'px', size: 800 },
      background_background: 'classic',
      background_color: C.white,
      padding: { unit: 'px', top: '80', right: '24', bottom: '80', left: '24', isLinked: false },
    },
    column(100, {},
      pillLabel('מבוא', C.tealLight, C.tealDeep),
      spacer(16),
      widget('heading', {
        title: 'למה איכות הביציות חשובה כל כך?',
        header_size: 'h2', align: 'right', title_color: C.tealDeep,
        typography_font_family: 'Heebo', typography_font_weight: '900',
        typography_font_size: { unit: 'px', size: 44 },
      }),
      spacer(16),
      textEditor(
        `<p style="font-size:18px;color:${C.charcoal};line-height:1.7">איכות הביציות היא קריטית להצלחת קליטה להריון ולחוויית הריון בריא. איכות ביציות טובה יותר מגבירה משמעותית את הסיכוי להריון באופן טבעי, וגם מגדילה את הסיכוי לשאוב כמות גדולה יותר של ביציות איכותיות בזמן טיפולי פוריות ושימור פוריות.</p>`,
      ),
      spacer(8),
      textEditor(
        `<p style="font-size:18px;color:${C.charcoal};line-height:1.7">לסביבה יש השפעה על איכות הביצית כבר כ-<strong style="color:${C.tealDeep}">90 יום</strong> לפני הביוץ או השאיבה. לכן שינויים בתזונה, בתוספי תזונה ובאורח החיים יכולים להשפיע על הביציות שיבשילו בחודשים הקרובים.</p>`,
      ),
      spacer(24),
      innerSection(
        { background_background: 'classic', background_color: C.tealLight, border_radius: { unit: 'px', top: 16, right: 16, bottom: 16, left: 16 }, padding: { unit: 'px', top: 24, right: 24, bottom: 24, left: 24, isLinked: false } },
        column(100, {},
          textEditor(`<p style="font-size:16px;font-weight:500;color:${C.tealDeep};font-family:Heebo,sans-serif">המדריך מחולק ל־3 חלקים עיקריים:</p>`),
          spacer(12),
          innerSection({ gap: '12px', padding: { unit: 'px', top: 0, right: 0, bottom: 0, left: 0, isLinked: true } },
            column(33, {},
              textEditor(`<span style="display:inline-flex;align-items:center;gap:8px;background:white;border-radius:20px;padding:8px 16px;font-size:14px;font-weight:700;color:${C.tealDeep};font-family:Heebo,sans-serif;box-shadow:0 1px 4px rgba(0,0,0,0.08)"><span style="width:20px;height:20px;border-radius:50%;background:${C.teal};color:white;font-size:11px;font-weight:900;display:inline-flex;align-items:center;justify-content:center">1</span>תזונה</span>`),
            ),
            column(33, {},
              textEditor(`<span style="display:inline-flex;align-items:center;gap:8px;background:white;border-radius:20px;padding:8px 16px;font-size:14px;font-weight:700;color:${C.tealDeep};font-family:Heebo,sans-serif;box-shadow:0 1px 4px rgba(0,0,0,0.08)"><span style="width:20px;height:20px;border-radius:50%;background:${C.teal};color:white;font-size:11px;font-weight:900;display:inline-flex;align-items:center;justify-content:center">2</span>תוספי תזונה חשובים</span>`),
            ),
            column(33, {},
              textEditor(`<span style="display:inline-flex;align-items:center;gap:8px;background:white;border-radius:20px;padding:8px 16px;font-size:14px;font-weight:700;color:${C.tealDeep};font-family:Heebo,sans-serif;box-shadow:0 1px 4px rgba(0,0,0,0.08)"><span style="width:20px;height:20px;border-radius:50%;background:${C.teal};color:white;font-size:11px;font-weight:900;display:inline-flex;align-items:center;justify-content:center">3</span>אורח חיים</span>`),
            ),
          ),
        ),
      ),
    ),
  ),

  // ─── SECTION 2: תזונה ─────────────────────────────────────────────────────────
  section(
    {
      layout: 'full_width',
      content_width: { unit: 'px', size: 900 },
      background_background: 'classic',
      background_color: C.tealMid,
      padding: { unit: 'px', top: '80', right: '24', bottom: '80', left: '24', isLinked: false },
    },
    column(100, {},
      pillLabel('חלק ראשון', C.teal, C.white),
      spacer(16),
      widget('heading', {
        title: 'תזונה', header_size: 'h2', align: 'right', title_color: C.tealDeep,
        typography_font_family: 'Heebo', typography_font_weight: '900',
        typography_font_size: { unit: 'px', size: 48 },
      }),
      textEditor(
        `<p style="font-size:18px;color:${C.charcoal};line-height:1.7">תזונה נכונה מהווה את הבסיס לתמיכה בסביבה האופטימלית להבשלת ביציות איכותיות.</p>`,
      ),
      spacer(32),
      accordionItem('01', C.tealLight, 'ירקות', 'הבסיס של תזונת פוריות'),
      spacer(12),
      accordionItem('02', C.greenLight, 'חלבון איכותי', 'חיוני להבשלת ביציות'),
      spacer(12),
      accordionItem('03', C.tealLight, 'שומנים טובים', 'חשובים לייצור הורמונים'),
      spacer(12),
      accordionItem('04', C.greenLight, 'פחמימות', 'לבחור נכון'),
      spacer(12),
      accordionItem('05', C.tealLight, 'נוגדי חמצון', 'הגנה על הביציות'),
      spacer(12),
      accordionItem('06', C.greenLight, 'מזונות להפחתה', 'מה כדאי להוריד'),
    ),
  ),

  // ─── SECTION 3: תוספי תזונה ───────────────────────────────────────────────────
  section(
    {
      layout: 'full_width',
      content_width: { unit: 'px', size: 1100 },
      background_background: 'classic',
      background_color: C.white,
      padding: { unit: 'px', top: '80', right: '24', bottom: '80', left: '24', isLinked: false },
    },
    column(100, {},
      pillLabel('חלק שני', C.teal, C.white),
      spacer(16),
      widget('heading', {
        title: 'תוספי תזונה', header_size: 'h2', align: 'right', title_color: C.tealDeep,
        typography_font_family: 'Heebo', typography_font_weight: '900',
        typography_font_size: { unit: 'px', size: 48 },
      }),
      textEditor(
        `<p style="font-size:18px;color:${C.charcoal};line-height:1.7">פורמולות מבוססות מחקר שפיתחנו במיוחד לתמיכה בפוריות הנשית — שני תוספים שפועלים בסינרגיה.</p>`,
      ),
      spacer(40),
      // Product cards row
      innerSection(
        { gap: '24px', padding: { unit: 'px', top: 0, right: 0, bottom: 0, left: 0, isLinked: true } },
        column(50, {},
          productCard(
            IMG.forYou, 'ForYou',
            'הכנת הגוף להריון',
            `פורמולת "חובה" המשלבת את רכיבי המפתח להכנת הגוף להריון: חומצה פולית טבעית, קו-אנזים Q10, כולין ויוד. השילוב המדויק התומך ביצירת הסביבה האופטימלית להריון בריא.`,
          ),
        ),
        column(50, {},
          productCard(
            IMG.ovaLife, 'OvaLife',
            'תמיכה ברזרבה השחלתית',
            `פורמולה מתקדמת המשלבת רכיבים מבוססי מחקר בתחום Ovary Antiaging, הפועלים בסינרגיה לתמיכה ברזרבה השחלתית. השילוב תומך באיכות הביציות ובמאגרי הגוף לקראת ההפריה.`,
          ),
        ),
      ),
      spacer(40),
      // Bundle CTA
      innerSection(
        { background_background: 'classic', background_color: C.tealLight, border_radius: { unit: 'px', top: 20, right: 20, bottom: 20, left: 20 }, padding: { unit: 'px', top: 32, right: 32, bottom: 32, left: 32, isLinked: false } },
        column(100, {},
          widget('heading', {
            title: 'מארז תמיכה ברזרבה השחלתית',
            header_size: 'h3', align: 'right', title_color: C.tealDeep,
            typography_font_family: 'Heebo', typography_font_weight: '900',
            typography_font_size: { unit: 'px', size: 24 },
          }),
          textEditor(
            `<p style="font-size:16px;color:${C.charcoal}">שני התוספים יחד — ForYou + OvaLife — במארז מיוחד</p>`,
          ),
          spacer(16),
          button(
            'לרכישת המארז לתמיכה ברזרבה',
            'https://novalife.co.il/product/%d7%9e%d7%90%d7%a8%d7%96-%d7%aa%d7%9e%d7%99%d7%9b%d7%94-%d7%91%d7%a8%d7%96%d7%a8%d7%91%d7%94/',
            { bgColor: C.tealDeep, textColor: C.white, align: 'right' },
          ),
        ),
      ),
    ),
  ),

  // ─── SECTION 4: אורח חיים ─────────────────────────────────────────────────────
  section(
    {
      layout: 'full_width',
      content_width: { unit: 'px', size: 1100 },
      background_background: 'classic',
      background_color: C.greenMid,
      padding: { unit: 'px', top: '80', right: '24', bottom: '80', left: '24', isLinked: false },
    },
    column(100, {},
      pillLabel('חלק שלישי', C.teal, C.white),
      spacer(16),
      widget('heading', {
        title: 'אורח חיים', header_size: 'h2', align: 'right', title_color: C.tealDeep,
        typography_font_family: 'Heebo', typography_font_weight: '900',
        typography_font_size: { unit: 'px', size: 48 },
      }),
      textEditor(
        `<p style="font-size:18px;color:${C.charcoal};line-height:1.7">גורמי אורח חיים המשפיעים על איכות הביציות — שינויים קטנים ועקביים שיכולים לעשות הבדל משמעותי.</p>`,
      ),
      spacer(40),
      // Lifestyle cards – row 1
      innerSection(
        { gap: '24px', padding: { unit: 'px', top: 0, right: 0, bottom: 0, left: 0, isLinked: true } },
        column(33, {},
          lifestyleCard('🌙', '7–8 שעות בלילה', 'שינה',
            'שינה מספקת חשובה לתהליכים הורמונליים בגוף. במהלך השינה מתרחשים תהליכי תיקון והתחדשות של תאים ומערכות בגוף. חוסר שינה כרוני עלול להשפיע על האיזון ההורמונלי.'),
        ),
        column(33, {},
          lifestyleCard('🧘‍♀️', 'הפחתת מתח מתמשך', 'סטרס',
            'סטרס ממושך משפיע על מערכת העצבים ועל הציר ההורמונלי בגוף. כאשר הגוף נמצא במצב של מתח מתמשך משתחררים הורמוני סטרס כגון קורטיזול, אשר עשויים להשפיע על מערכות שונות.'),
        ),
        column(33, {},
          lifestyleCard('🏃‍♀️', 'פעילות מתונה ועקבית', 'פעילות גופנית',
            'פעילות גופנית מתונה יכולה לתרום לבריאות הכללית, לשיפור זרימת הדם ולתמיכה במטבוליזם. היא יכולה גם לסייע באיזון רמות סוכר ובהפחתת סטרס.'),
        ),
      ),
      spacer(24),
      // Lifestyle cards – row 2
      innerSection(
        { gap: '24px', padding: { unit: 'px', top: 0, right: 0, bottom: 0, left: 0, isLinked: true } },
        column(33, {},
          lifestyleCard('🚭', 'גורם משמעותי לפגיעה בפוריות', 'עישון',
            'עישון הוא אחד הגורמים הידועים ביותר המשפיעים לרעה על פוריות. חומרים המצויים בעשן הסיגריות עלולים להגביר סטרס חמצוני ולפגוע בתאי הגוף, כולל תאי הביצית.'),
        ),
        column(33, {},
          lifestyleCard('⚖️', 'איזון תומך פוריות', 'משקל גוף ואיזון מטבולי',
            'משקל גוף נמוך מאוד או עודף משקל משמעותי יכולים להשפיע על האיזון ההורמונלי. שמירה על תזונה מאוזנת ופעילות גופנית מתונה יכולה לסייע באיזון מטבולי התומך בפוריות.'),
        ),
        column(33, {},
          lifestyleCard('🌿', 'הפחתת חשיפה סביבתית', 'חשיפה למשבשים הורמונליים',
            'ישנם חומרים סביבתיים הנקראים משבשים הורמונליים, אשר יכולים להשפיע על פעילות ההורמונים בגוף. מומלץ להפחית חשיפה לחומרים אלו.'),
        ),
      ),
    ),
  ),

  // ─── SECTION 5: לסיכום ────────────────────────────────────────────────────────
  section(
    {
      layout: 'full_width',
      content_width: { unit: 'px', size: 900 },
      background_background: 'classic',
      background_color: C.white,
      padding: { unit: 'px', top: '80', right: '24', bottom: '80', left: '24', isLinked: false },
    },
    column(100, {},
      pillLabel('לסיכום', C.tealLight, C.tealDeep),
      spacer(16),
      widget('heading', {
        title: 'אנחנו כאן עבורכן 🤍',
        header_size: 'h2', align: 'right', title_color: C.tealDeep,
        typography_font_family: 'Heebo', typography_font_weight: '900',
        typography_font_size: { unit: 'px', size: 48 },
      }),
      spacer(16),
      textEditor(`<p style="font-size:18px;color:${C.charcoal};line-height:1.7">איכות הביציות מושפעת משילוב של תזונה, תוספי תזונה ואורח חיים. במדריך הזה ניסינו לשתף אתכן בכלים ובעקרונות שאנחנו משתמשות בהם בקליניקה כדי לתמוך בפוריות ובבריאות הגוף.</p>`),
      spacer(8),
      textEditor(`<p style="font-size:18px;color:${C.charcoal};line-height:1.7">המטרה שלנו הייתה לתת לכן ידע וכלים שתוכלו להתחיל ליישם כבר ביומיום, בקצב שמתאים לכן. לעיתים דווקא השינויים הקטנים והעקביים הם אלו שיכולים לעשות הבדל לאורך זמן.</p>`),
      spacer(8),
      textEditor(`<p style="font-size:18px;color:${C.charcoal};line-height:1.7">יחד עם זאת, חשוב לומר בכנות — הדרך להריון לא תמיד פשוטה. לכל אישה יש גוף שונה, שגרת חיים שונה והעדפות שונות, ולכן פעמים רבות ההתאמה האישית היא חלק חשוב מהתהליך.</p>`),
      spacer(16),
      textEditor(`<p style="font-size:16px;font-weight:500;color:${C.charcoal};line-height:1.7">אם תרגישו שתרצו ליווי, הכוונה או התאמה מדויקת יותר עבורכן — אנחנו כאן. נשמח לעזור לכן לבנות תוכנית שתשתלב בשגרת החיים שלכן ותתמוך בגוף שלכן בצורה הטובה ביותר.</p>`),
      spacer(8),
      textEditor(`<p style="font-size:16px;font-weight:700;color:${C.charcoal}">מאחלות לכן בריאות, איזון והצלחה בדרך להריון. 🤍</p>`),
      spacer(32),
      button(
        'לרכישת המארז לתמיכה ברזרבה',
        'https://novalife.co.il/product/%d7%9e%d7%90%d7%a8%d7%96-%d7%aa%d7%9e%d7%99%d7%9b%d7%94-%d7%91%d7%a8%d7%96%d7%a8%d7%91%d7%94/',
        { bgColor: C.tealDeep, textColor: C.white, align: 'right' },
      ),
      spacer(16),
      textEditor(`<p style="font-size:14px;color:${C.charcoal};text-align:right" dir="rtl">לפרטים נוספים צרו קשר בווטסאפ: <strong>+972 55-775-7669</strong></p>`),
    ),
  ),

  // ─── FOOTER ────────────────────────────────────────────────────────────────────
  section(
    {
      layout: 'full_width',
      content_width: { unit: 'px', size: 1100 },
      background_background: 'classic',
      background_color: C.footerBg,
      padding: { unit: 'px', top: '48', right: '24', bottom: '48', left: '24', isLinked: false },
    },
    column(50, { vertical_alignment: 'middle' },
      image(IMG.logo, 'NovaLife', { align: 'right', width: 114 }),
      textEditor(`<p style="font-size:14px;color:rgba(255,255,255,0.6);text-align:right">ליווי מקצועי ורגשי בדרך להריון</p>`),
    ),
    column(50, { vertical_alignment: 'middle' },
      widget('heading', {
        title: '<a href="https://novalife.co.il" target="_blank" rel="noopener" style="color:rgba(255,255,255,0.8);text-decoration:underline">novalife.co.il</a>',
        header_size: 'p', align: 'left', title_color: C.white,
        typography_font_family: 'Heebo', typography_font_size: { unit: 'px', size: 14 },
      }),
    ),
  ),

  // ─── COPYRIGHT BAR ─────────────────────────────────────────────────────────────
  section(
    {
      layout: 'full_width',
      content_width: { unit: 'px', size: 1100 },
      background_background: 'classic',
      background_color: C.footerBg,
      padding: { unit: 'px', top: '16', right: '24', bottom: '16', left: '24', isLinked: false },
      border_border: 'solid',
      border_width: { unit: 'px', top: '1', right: '0', bottom: '0', left: '0', isLinked: false },
      border_color: 'rgba(255,255,255,0.2)',
    },
    column(100, {},
      textEditor(
        `<p style="font-size:12px;color:rgba(255,255,255,0.5);text-align:center">© 2026 NovaLife. כל הזכויות שמורות. · המידע במדריך זה הוא לצורכי מידע כללי ואינו מהווה ייעוץ רפואי.</p>`,
      ),
    ),
  ),
];

// ── Final template ─────────────────────────────────────────────────────────────
const template = {
  version: '0.4',
  title: 'NovaLife — מדריך לשיפור איכות וכמות הביציות',
  type: 'page',
  content,
  page_settings: {
    custom_css: `
/* RTL support */
body, .elementor { direction: rtl; }

/* Pill label override (fallback if widget custom_css doesn't fire) */
.nv-pill-label .elementor-heading-title {
  display: inline-block !important;
  background: #DBF0F3;
  color: #1A717D;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
}
    `.trim(),
  },
};

writeFileSync(
  '/home/avil/git/misc/base44_to_elementor/elementor-template.json',
  JSON.stringify(template, null, 2),
);
console.log('✓ elementor-template.json written');
