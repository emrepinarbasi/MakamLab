package com.makambox.studio.report.docx;

import com.makambox.studio.i18n.LocalizationService;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/** Shared locale-sensitive text and numeric formatting for DOCX reports. */
public final class ReportLocalization {
    private ReportLocalization() { }

    public static LocalizationService service() {
        return LocalizationService.getInstance();
    }

    public static Locale locale() {
        return service().locale();
    }

    /**
     * Translate a report key. Turkish and English fallbacks are kept in code
     * so old reports remain readable even if a newly introduced bundle key is
     * missing. All other supported languages resolve through the packaged
     * catalogue and fall back to English rather than leaking Turkish prose.
     */
    public static String text(String key, String englishFallback, String turkishFallback,
                       Object... arguments) {
        String language = locale().getLanguage();
        if ("tr".equals(language) || "en".equals(language)) {
            String pattern = "tr".equals(language) ? turkishFallback : englishFallback;
            if (arguments == null || arguments.length == 0) return pattern;
            return new MessageFormat(pattern, locale()).format(arguments);
        }
        return service().textOrDefault(key, englishFallback, arguments);
    }

    public static String key(String key, String englishFallback, String turkishFallback) {
        return text(key, englishFallback, turkishFallback);
    }

    public static String number(double value, int digits) {
        if (!Double.isFinite(value)) return "—";
        DecimalFormat format = new DecimalFormat();
        format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(locale()));
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    public static String signed(double value, int digits) {
        if (!Double.isFinite(value)) return "—";
        return (value >= 0 ? "+" : "") + number(value, digits);
    }

    /** Values received here are already in the 0..100 report convention. */
    public static String percentValue(double percent, int digits) {
        if (!Double.isFinite(percent)) return "—";
        return service().textOrDefault("format.percentValue", "{0}%", number(percent, digits));
    }

    public static String dateTime(TemporalAccessor value) {
        return service().formatDateTime(value);
    }

    public static String lowerCase(String value) {
        return value == null ? "" : value.toLowerCase(locale());
    }

    /** Localizes internal analysis labels without changing makam or pitch proper names. */
    public static String displayLabel(String value) {
        if (value == null || value.isBlank()) return "—";
        return switch (value.strip()) {
            case "Çıkıcı" -> text("report.ascending", "Ascending", "Çıkıcı");
            case "İnici" -> text("report.descending", "Descending", "İnici");
            case "Yatay/Karma" -> text("report.horizontalMixed", "Horizontal/Mixed", "Yatay/Karma");
            case "Segâh kalışı" -> text("report.sustained", "Segâh sustain", "Segâh kalışı");
            case "Çıkıcı cümle" -> text("report.ascendingPhrase", "Ascending phrase", "Çıkıcı cümle");
            case "İnici cümle" -> text("report.descendingPhrase", "Descending phrase", "İnici cümle");
            case "Doğrudan Segâh kalışı" -> text("report.directSegahSustain", "Direct Segâh sustain", "Doğrudan Segâh kalışı");
            case "Belirsiz/diğer" -> text("report.uncertainOther", "Uncertain/other", "Belirsiz/diğer");
            case "Bağlam yetersiz" -> text("report.insufficientContext", "Insufficient context", "Bağlam yetersiz");
            case "Kısa temas" -> text("report.eventType.shortContact", "Brief contact", "Kısa temas");
            case "Geçiş" -> text("report.eventType.transition", "Transition", "Geçiş");
            case "Uzatma" -> text("report.eventType.sustained", "Sustained", "Uzatma");
            case "Kalış" -> text("report.eventType.cadentialHold", "Cadential hold", "Kalış");
            case "Ölçülebilir" -> text("report.measurable", "Measurable", "Ölçülebilir");
            case "Pest" -> text("report.flatter", "Flatter", "Pest");
            case "Dik" -> text("report.sharper", "Sharper", "Dik");
            case "Kuramsal merkez" -> text("report.theoreticalCentre", "Theoretical centre", "Kuramsal merkez");
            case "Tümü" -> text("report.all", "All", "Tümü");
            case "Son kararlı sesten otomatik" -> text("report.tonicSource.automaticFinal", "Automatic from the final stable sound", "Son kararlı sesten otomatik");
            case "Grafikten seçilen bölge" -> text("report.tonicSource.graphRegion", "Region selected on the graph", "Grafikten seçilen bölge");
            case "Elle girilen frekans" -> text("report.tonicSource.manualHertz", "Manually entered frequency", "Elle girilen frekans");
            case "Kullanıcı tarafından onaylandı" -> text("report.userConfirmed", "Confirmed by the user", "Kullanıcı tarafından onaylandı");
            case "Otomatik aday" -> text("report.automaticCandidate", "Automatic candidate", "Otomatik aday");
            case "Belirtilmedi" -> text("common.unspecified", "Not specified", "Belirtilmedi");
            case "Belirtilmeyen profil" -> text("report.unspecifiedProfile",
                    "Unspecified profile", "Belirtilmeyen profil");
            case "Stereo karışım" -> service().textOrDefault("audio.channel.mix", "Stereo mix");
            case "Sol kanal" -> service().textOrDefault("audio.channel.left", "Left channel");
            case "Sağ kanal" -> service().textOrDefault("audio.channel.right", "Right channel");
            case "Tek kanal" -> service().textOrDefault("audio.channel.mono", "Mono");
            case "Otomatik (melodik kanal)" -> service().textOrDefault(
                    "audio.channel.autoMelodic", "Automatic (melodic channel)");
            case "Son kararlı sesten otomatik öneri, kullanıcı onaylı" -> text(
                    "report.tonicMethod.automaticConfirmed",
                    "Automatic suggestion from the final stable sound, confirmed by the user",
                    "Son kararlı sesten otomatik öneri, kullanıcı onaylı");
            // Legacy reports used this shorter wording for the same method.
            // Reuse the canonical localized label so older analysis data cannot
            // leak Turkish into a report generated in another language.
            case "Son kararlı ses + kullanıcı onayı" -> text(
                    "report.tonicMethod.automaticConfirmed",
                    "Automatic suggestion from the final stable sound, confirmed by the user",
                    "Son kararlı sesten otomatik öneri, kullanıcı onaylı");
            case "Grafikte seçilen bölgenin sağlam medyanı, kullanıcı onaylı" -> text(
                    "report.tonicMethod.graphMedianConfirmed",
                    "Robust median of the region selected on the graph, confirmed by the user",
                    "Grafikte seçilen bölgenin sağlam medyanı, kullanıcı onaylı");
            default -> value;
        };
    }

    /** Localizes the finite set of audit/exclusion reasons produced by the engines. */
    public static String reason(String value) {
        if (value == null || value.isBlank()) return "—";
        String normalized = value.strip();
        if (normalized.startsWith("Makam profillerini ayırmak için kanıt yetersiz:")) {
            boolean needsEvents = normalized.contains("en az 10 kararlı olay");
            boolean needsDuration = normalized.contains("en az 5 saniye kararlı kanıt");
            String requirement = needsEvents && needsDuration
                    ? text("report.reason.profileNeedsEventsAndDuration",
                    "at least 10 stable events and at least 5 seconds of stable evidence",
                    "en az 10 kararlı olay ve en az 5 saniye kararlı kanıt")
                    : needsEvents ? text("report.reason.profileNeedsEvents", "at least 10 stable events",
                    "en az 10 kararlı olay")
                    : needsDuration ? text("report.reason.profileNeedsDuration",
                    "at least 5 seconds of stable evidence", "en az 5 saniye kararlı kanıt")
                    : text("report.reason.moreStableEvidence", "more stable evidence", "daha fazla kararlı kanıt");
            return text("report.reason.insufficientProfileEvidence",
                    "Insufficient evidence to distinguish makam profiles: {0}.",
                    "Makam profillerini ayırmak için kanıt yetersiz: {0}.", requirement);
        }
        return switch (normalized) {
            case "Kısa temas; ana ortalamaya alınmadı" -> text("report.reason.shortContact",
                    "Brief contact; excluded from the primary mean",
                    "Kısa temas; ana ortalamaya alınmadı");
            case "Yerleşik Segâh gövdesi yok; glissando/geçiş adayı" -> text("report.reason.noSettledSegah",
                    "No settled Segâh body; glissando/transition candidate",
                    "Yerleşik Segâh gövdesi yok; glissando/geçiş adayı");
            case "Dügâh–Bûselik açık aralığında, fakat Segâh işlev bandının dışında" -> text("report.reason.outsideSegahBand",
                    "Inside the open Dügâh–Bûselik interval but outside the Segâh functional band",
                    "Dügâh–Bûselik açık aralığında, fakat Segâh işlev bandının dışında");
            case "F0 güveni %65'in altında; denetim kanıtı" -> text("report.reason.lowF0Confidence65",
                    "F0 confidence below 65%; audit evidence",
                    "F0 güveni %65'in altında; denetim kanıtı");
            case "Cümle sınıflandırma güveni yetersiz" -> text("report.reason.lowPhraseConfidence",
                    "Insufficient phrase-classification confidence",
                    "Cümle sınıflandırma güveni yetersiz");
            case "Dügâh yön çıpası eksik veya iki yönde çelişkili" -> text(
                    "report.reason.missingOrConflictingDugahAnchor",
                    "The Dügâh direction anchor is missing or conflicts in both directions",
                    "Dügâh yön çıpası eksik veya iki yönde çelişkili");
            case "Perde gövdesi ana istatistik için fazla değişken" -> text(
                    "report.reason.pitchBodyTooVariable",
                    "The pitch body is too variable for the primary statistics",
                    "Perde gövdesi ana istatistik için fazla değişken");
            case "Araştırma ölçütlerini karşılamadı" -> text(
                    "report.reason.studyCriteriaNotMet", "The study criteria were not met",
                    "Araştırma ölçütlerini karşılamadı");
            case "F0 güveni %35–65 aralığında; denetim adayı" -> text("report.reason.auditF0Confidence",
                    "F0 confidence between 35–65%; audit candidate",
                    "F0 güveni %35–65 aralığında; denetim adayı");
            case "Düşük güvenli derece; denetim cümlesi" -> text("report.reason.lowConfidenceDegree",
                    "Low-confidence degree; audit phrase",
                    "Düşük güvenli derece; denetim cümlesi");
            case "Düşük güvenli doğrudan artık aralık; denetim adayı" -> text("report.reason.lowConfidenceAugmentedInterval",
                    "Low-confidence direct augmented interval; audit candidate",
                    "Düşük güvenli doğrudan artık aralık; denetim adayı");
            case "Temas süresi ana istatistik için çok kısa" -> text(
                    "report.reason.contactTooShortForPrimary",
                    "Contact duration is too short for the primary statistics",
                    "Temas süresi ana istatistik için çok kısa");
            case "En yakın profiller arasındaki fark küçüktür; aralık profili tek başına ayırt edici değildir." -> text(
                    "report.reason.closestProfilesNotDistinct",
                    "The difference between the closest profiles is small; the interval profile alone is not distinctive.",
                    "En yakın profiller arasındaki fark küçüktür; aralık profili tek başına ayırt edici değildir.");
            case "Güvenilir F0 kapsamı düşük" -> text("report.reason.lowReliableF0Coverage",
                    "Low reliable-F0 coverage", "Güvenilir F0 kapsamı düşük");
            case "Süre kısa" -> text("report.reason.durationTooShort", "Duration too short", "Süre kısa");
            case "Süre uzun" -> text("report.reason.durationTooLong", "Duration too long", "Süre uzun");
            case "Genişlik yetersiz" -> text("report.reason.insufficientSpan", "Insufficient span", "Genişlik yetersiz");
            case "Tek yönlülük düşük" -> text("report.reason.lowMonotonicity", "Low monotonicity", "Tek yönlülük düşük");
            case "Sürekli hareket kanıtı yetersiz" -> text("report.reason.insufficientContinuousMotion",
                    "Insufficient evidence of continuous motion", "Sürekli hareket kanıtı yetersiz");
            case "Ani perde sıçraması" -> text("report.reason.abruptPitchJump", "Abrupt pitch jump", "Ani perde sıçraması");
            case "F0 boşluğu uzun" -> text("report.reason.longF0Gap", "Long F0 gap", "F0 boşluğu uzun");
            case "Arada kararlı duruş var" -> text("report.reason.intermediateStableHold",
                    "Intermediate stable hold detected", "Arada kararlı duruş var");
            case "Başlangıç perdesinde kararlı temas yok" -> text("report.reason.noStableOriginContact",
                    "No stable contact at the origin pitch", "Başlangıç perdesinde kararlı temas yok");
            case "Bitiş perdesinde kararlı temas yok" -> text("report.reason.noStableDestinationContact",
                    "No stable contact at the destination pitch", "Bitiş perdesinde kararlı temas yok");
            case "Belirtilmedi" -> text("common.unspecified", "Not specified", "Belirtilmedi");
            default -> normalized;
        };
    }
}
