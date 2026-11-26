package com.makambox.studio.ui;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.report.docx.AcademicStudyDocument;
import com.makambox.studio.report.docx.DocxStudyReportExporter;
import com.makambox.studio.report.docx.ReportLocalization;
import com.makambox.studio.report.docx.SpecializedAnalysisDocxExporter;
import com.makambox.studio.report.docx.SpecializedAnalysisMetadata;
import com.makambox.studio.study.CadenceSimilarity;
import com.makambox.studio.study.FirstFourMakamGroupCatalog;
import com.makambox.studio.study.FirstFourDirectionalAnalyzer;
import com.makambox.studio.study.FirstFourProfileReferenceCatalog;
import com.makambox.studio.study.MakamProfileComparison;
import com.makambox.studio.study.MakamProfileComparator;
import com.makambox.studio.study.IntervalEventTier;
import com.makambox.studio.study.IntervalPitchNaming;
import com.makambox.studio.study.PitchIntervalStudyRequest;
import com.makambox.studio.study.PitchIntervalStudyResult;
import com.makambox.studio.study.PitchIntervalStudyService;
import com.makambox.studio.study.SinglePitchStudyRequest;
import com.makambox.studio.study.SinglePitchStudyResult;
import com.makambox.studio.study.SinglePitchStudyService;
import com.makambox.studio.study.StudyPitchEvent;
import com.makambox.studio.study.TonicSelection;
import com.makambox.studio.study.UssakSegahDirectionalAnalyzer;
import com.makambox.studio.study.UssakSegahDirectionalStudyResult;
import com.makambox.studio.study.UssakSegahEvent;
import com.makambox.studio.study.UssakSegahUsageClass;
import com.makambox.studio.theory.TheoryCatalog;

import javax.swing.JOptionPane;
import java.awt.Desktop;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Connects the guided Makam workspace to the editable academic DOCX report. */
public final class MakamDocxReportHandler implements MakamWordReportHandler {
    private static final String APP_VERSION = "MakamLab 1.1.3";
    private static final String ENGINE_VERSION = "YIN / 53-Hc 1.0";
    private final SinglePitchStudyService service = new SinglePitchStudyService();
    private final PitchIntervalStudyService intervalService = new PitchIntervalStudyService();
    private final UssakSegahDirectionalAnalyzer directionalAnalyzer =
            new UssakSegahDirectionalAnalyzer();
    private final FirstFourDirectionalAnalyzer firstFourDirectionalAnalyzer =
            new FirstFourDirectionalAnalyzer();
    private final DocxStudyReportExporter exporter = new DocxStudyReportExporter();
    private final SpecializedAnalysisDocxExporter specializedExporter =
            new SpecializedAnalysisDocxExporter();

    @Override public void export(Window owner, MakamStudyContext context) throws Exception {
        String selectionName = switch (context.analysisMode()) {
            case BETWEEN_PITCHES -> context.lowerBoundary() + "-"
                    + context.upperBoundary();
            case USSAK_SEGAH_DIRECTIONAL -> "Segâh";
            case HICAZ_AUGMENTED_INTERVAL -> "Hicaz-2-3";
            case GLISSANDO_ANALYSIS -> context.glissandoStudy() == null
                    ? "F0-F0" : context.glissandoStudy().request().lowerPitchName()
                    + "-" + context.glissandoStudy().request().upperPitchName();
            case SINGLE_PITCH -> context.target() == null ? "F0" : context.target().toString();
        };
        String suggested = safeStem(context.audio().name()) + "-"
                + safeStem(context.makam().toString()) + "-"
                + safeStem(selectionName) + "-MakamLab.docx";
        Path path = NativeFileDialogs.save(owner, t("report.saveDialog",
                "Save detailed Makam Word report", "Ayrıntılı Makam Word raporunu kaydet"), suggested);
        if (path == null) return;
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx")) {
            path = path.resolveSibling(path.getFileName() + ".docx");
        }

        if (context.analysisMode() == MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL
                || context.analysisMode() == MakamAnalysisMode.GLISSANDO_ANALYSIS) {
            exportSpecialized(context, path);
            showLocalizedMessage(owner,
                    t("report.createdMessage", "Detailed Word report created:\n{0}",
                            "Ayrıntılı Word raporu oluşturuldu:\n{0}", path),
                    t("report.readyTitle", "MakamLab report is ready", "MakamLab raporu hazır"),
                    JOptionPane.INFORMATION_MESSAGE);
            openSavedReport(path);
            return;
        }

        TheoryCatalog theory = TheoryCatalog.loadDefault();
        TheoryCatalog.Makam makam = theory.makams().stream()
                .filter(value -> value.name().equalsIgnoreCase(context.makam().name()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(t("report.error.makamNotFound",
                        "The makam was not found in the theory catalogue",
                        "Makam kuram kataloğunda bulunamadı")));
        TonicSelection tonic = tonicSelection(context);
        AcademicStudyDocument document;
        if (context.analysisMode() == MakamAnalysisMode.BETWEEN_PITCHES) {
            if (context.lowerBoundary() == null || context.upperBoundary() == null) {
                throw new IllegalArgumentException(t("report.error.intervalBoundaries",
                        "Pitch-interval boundaries were not specified",
                        "Perde aralığı sınırları belirlenmedi"));
            }
            PitchIntervalStudyRequest request = new PitchIntervalStudyRequest(
                    context.audio(), context.frames(), theory, makam,
                    theory.note(context.lowerBoundary().key()), theory.note(context.upperBoundary().key()),
                    tonic, context.report().config(), APP_VERSION,
                    MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                    context.firstFourProfileSelection());
            PitchIntervalStudyResult study = intervalService.analyze(request);
            document = adaptInterval(context, study, theory, makam,
                    firstFourDirectionalAnalyzer.compare(study.allCanonicalEvents(),
                            study.profileComparison()));
        } else if (context.analysisMode() == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL) {
            TheoryCatalog.Note target = theory.note("segah");
            SinglePitchStudyRequest request = new SinglePitchStudyRequest(
                    context.audio(), context.frames(), theory, makam, target, tonic,
                    context.report().config(), APP_VERSION, MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                    context.firstFourProfileSelection());
            SinglePitchStudyResult baseStudy = service.analyze(request);
            UssakSegahDirectionalStudyResult directional = context.directionalSegahStudy();
            if (directional == null) {
                directional = directionalAnalyzer.analyze(context.frames(), context.allPitchEvents(),
                        context.report().config(), theory, makam, tonic.hertz());
            }
            document = adaptDirectional(context, baseStudy, directional, theory, makam);
        } else {
            TheoryCatalog.Note target = theory.note(context.target().key());
            SinglePitchStudyRequest request = new SinglePitchStudyRequest(
                    context.audio(), context.frames(), theory, makam, target, tonic,
                    context.report().config(), APP_VERSION, MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                    context.firstFourProfileSelection());
            SinglePitchStudyResult study = service.analyze(request);
            document = adapt(context, study, theory, makam,
                    firstFourDirectionalAnalyzer.compare(study.allCanonicalEvents(),
                            study.profileComparison()));
        }
        exporter.export(document, path);
        showLocalizedMessage(owner,
                t("report.createdMessage", "Detailed Word report created:\n{0}",
                        "Ayrıntılı Word raporu oluşturuldu:\n{0}", path),
                t("report.readyTitle", "MakamLab report is ready", "MakamLab raporu hazır"),
                JOptionPane.INFORMATION_MESSAGE);
        openSavedReport(path);
    }

    private void exportSpecialized(MakamStudyContext context, Path path) throws Exception {
        SpecializedAnalysisMetadata metadata = new SpecializedAnalysisMetadata(
                context.audio().name(), context.sourcePath(), context.audio().durationSeconds(),
                context.audio().sampleRate(), context.audio().channelDescription(),
                context.makam().toString(), TheoryCatalog.loadDefault().tonicFor(context.makam()).toString(),
                context.tonicHertz(), context.tonicMethod(), APP_VERSION);
        if (context.analysisMode() == MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL) {
            if (context.hicazStudy() == null) {
                throw new IllegalArgumentException(t("report.error.hicazNotComplete",
                        "The augmented-interval analysis is not complete yet",
                        "Hicaz artık aralık analizi henüz tamamlanmadı"));
            }
            specializedExporter.exportHicaz(metadata, context.hicazStudy(), path);
            return;
        }
        if (context.glissandoStudy() == null) {
            throw new IllegalArgumentException(t("report.error.glissandoNotComplete",
                    "The glissando/portamento analysis is not complete yet",
                    "Glisendo/Portamento analizi henüz tamamlanmadı"));
        }
        specializedExporter.exportGlissando(metadata, context.glissandoStudy(), path);
    }

    private static AcademicStudyDocument adaptDirectional(
            MakamStudyContext context,
            SinglePitchStudyResult base,
            UssakSegahDirectionalStudyResult directional,
            TheoryCatalog theory,
            TheoryCatalog.Makam makam) {
        List<AcademicStudyDocument.Event> events = directional.events().stream()
                .map(MakamDocxReportHandler::directionalEvent).toList();
        List<AcademicStudyDocument.DirectionalContext> directionalContexts =
                directional.events().stream().map(MakamDocxReportHandler::directionalContext).toList();
        TonicSelection tonic = base.tonicSelection();
        TheoryCatalog.Note dugah = theory.note("dugah");
        TheoryCatalog.Note buselik = theory.note("buselik");
        double dugahCent = theory.relativeCent(makam, dugah);
        double buselikCent = theory.relativeCent(makam, buselik);
        double dugahHz = tonic.hertz() * Math.pow(2.0, dugahCent / 1200.0);
        double buselikHz = tonic.hertz() * Math.pow(2.0, buselikCent / 1200.0);
        List<AcademicStudyDocument.TheoryPitch> pitchCenters =
                theoryPitches(theory, makam, tonic.hertz());
        List<AcademicStudyDocument.TheoryPitch> expected = List.of(
                new AcademicStudyDocument.TheoryPitch("Segâh",
                        directional.theoreticalSegahCent(), directional.theoreticalSegahHc(),
                        directional.theoreticalSegahHertz()));
        AcademicStudyDocument.Theory documentTheory = new AcademicStudyDocument.Theory(
                base.makamName(), base.tonicNote(), "Segâh",
                tonic.hertz(), directional.theoreticalSegahCent(),
                directional.theoreticalSegahHc(), directional.theoreticalSegahHertz(),
                base.profileComparison().referenceSystem() + " / 53-Hc",
                t("report.analysisType.directionalSegah",
                        "Ascending–descending Segâh analysis", "İnici-Çıkıcı Segâh analizi"),
                dugah.toString(), dugahCent, dugahCent / AnalysisConfig.COMMA_CENT, dugahHz,
                buselik.toString(), buselikCent, buselikCent / AnalysisConfig.COMMA_CENT, buselikHz,
                false, pitchCenters, expected);
        List<String> method = List.of(
                t("report.method.directional.candidate",
                        "Candidates are selected among pitches between Dügâh and Bûselik; the two boundary pitches themselves are excluded.",
                        "Araştırma adayı, Dügâh ile Bûselik arasındaki sesler içinden seçilir; iki sınır perdesinin kendisi hesaba katılmaz."),
                t("report.method.directional.deviation",
                        "Each candidate's measured frequency is compared with the theoretical Segâh centre. Negative deviation means a flatter Segâh; positive deviation means a sharper performance.",
                        "Her adayın ölçülen frekansı kuramsal Segâh merkeziyle karşılaştırılır. Eksi sapma Segâh'ın pest, artı sapma ise dik/tiz icra edildiğini gösterir."),
                t("report.method.directional.path",
                        "A reliable Dügâh-to-Segâh motion is classified as ascending, and a Segâh-to-Dügâh motion as descending. Following Çargâh and Nevâ in the same direction strengthens, but is not required for, phrase evidence.",
                        "Dügâh'tan Segâh'a yönelen güvenilir hareket çıkıcı; Segâh'tan Dügâh'a yönelen hareket inici kabul edilir. Çargâh ve Nevâ da aynı yönde izlenirse cümle kanıtı daha güçlü sayılır, fakat zorunlu değildir."),
                t("report.method.directional.sustain",
                        "Long, stable Segâh sounds without a clear ascending or descending path are evaluated separately as Segâh sustains.",
                        "Belirgin bir iniş veya çıkış yolu bulunmadan Segâh üzerinde uzun ve kararlı biçimde durulan sesler 'Segâh kalışı' olarak ayrı değerlendirilir."),
                t("report.method.directional.averages",
                        "Ascending, descending and sustain means are computed from their own events. The All row is not a mean of group means; it uses every accepted event once.",
                        "Çıkıcı, inici ve kalış ortalamaları kendi olaylarından hesaplanır. 'Tümü' satırı, grup ortalamalarının ortalaması değil, kabul edilen bütün olayların birer kez kullanıldığı genel sonuçtur."),
                t("report.method.directional.audit",
                        "Candidates with 35–65% confidence or only transitional character are retained in the audit table but excluded from the main mean.",
                        "Güveni yüzde 35–65 arasında kalan ya da yalnız geçiş niteliği taşıyan adaylar silinmez; denetim amacıyla tabloda gösterilir ancak ana ortalamaya katılmaz."));
        List<String> limitations = List.of(
                t("report.limit.directionalContext",
                        "This classification describes only the Segâh usage context heard in the recording; it is not by itself a makam or style diagnosis.",
                        "Bu sınıflandırma yalnız kayıtta duyulan Segâh kullanım bağlamını açıklar; tek başına makam veya üslûp teşhisi değildir."),
                t("report.limit.polyphonic",
                        "In polyphonic recordings, the result belongs to the dominant melodic F0 track in the selected channel and cannot be attributed directly to one performer.",
                        "Çok sesli kayıtlarda sonuç seçilen kanaldaki baskın melodik F0 izine aittir; tek bir icracıya doğrudan atfedilemez."),
                t("report.limit.smallSample",
                        "When a group contains fewer than five reliable events, the result is descriptive and should not be generalized to a wider performance population.",
                        "Bir grupta beşten az güvenilir olay varsa sonuç betimseldir; daha geniş bir icra evrenine genellenmemelidir."));
        return new AcademicStudyDocument(
                t("report.title.directionalDocument", "MakamLab — Ascending–Descending Segâh Pitch Analysis",
                        "MakamLab — İnici-Çıkıcı Segâh Perdesi Analizi"),
                new AcademicStudyDocument.Source(base.sourceName(), base.sourcePath(),
                        base.sourceSha256(), base.sourceDurationSeconds(), base.sourceSampleRate(),
                        base.analyzedChannel(), base.applicationVersion(), ENGINE_VERSION),
                documentTheory,
                new AcademicStudyDocument.TonicEvidence(tonic.source().label(), tonic.hertz(),
                        tonic.startSeconds(), tonic.endSeconds(), tonic.confidence(),
                        tonic.robustSpreadCent(), tonic.voicedFrameCount(), tonic.userConfirmed(),
                        context.tonicMethod()),
                events, tonicTrace(context.frames(), tonic), profileRows(base.profileComparison()),
                method, limitations, ZonedDateTime.now(), directionalContexts);
    }

    private static AcademicStudyDocument.Event directionalEvent(UssakSegahEvent value) {
        StudyPitchEvent measurement = value.measurement();
        String direction = switch (value.usageClass()) {
            case ASCENDING_PHRASE -> "Çıkıcı";
            case DESCENDING_PHRASE -> "İnici";
            case DIRECT_HOLD -> "Segâh kalışı";
            case OTHER -> "Belirsiz/diğer";
        };
        return new AcademicStudyDocument.Event(value.index(), "Segâh",
                measurement.startSeconds(), measurement.endSeconds(),
                measurement.observedHertz(), measurement.observedRelativeCent(),
                measurement.theoreticalRelativeCent(), measurement.deviationCent(),
                measurement.entryDeviationCent(), measurement.settledDeviationCent(),
                measurement.exitDeviationCent(), measurement.stabilityCent(),
                measurement.confidence(), direction, value.pathLabel(),
                // The directional analyzer is the authoritative inclusion gate.
                // A 100 ms plateau can be accepted there while the generic
                // single-pitch semantic helper still calls it a short contact
                // because of floating-point frame timing.  Never let that
                // secondary label silently remove an event from the DOCX
                // summary; the UI and report must describe the same frozen set.
                value.included() || measurement.measurable(),
                value.included(), value.included(),
                value.exclusionReason());
    }

    private static AcademicStudyDocument.DirectionalContext directionalContext(
            UssakSegahEvent value) {
        return new AcademicStudyDocument.DirectionalContext(value.index(),
                value.usageClass().label(), value.pathLabel(), value.previousAnchorName(),
                value.nextAnchorName(), value.localTonicHertz(),
                value.localDeviationCent(), value.classificationConfidence());
    }

    private static TonicSelection tonicSelection(MakamStudyContext context) {
        boolean region = Double.isFinite(context.tonicStartSeconds())
                && Double.isFinite(context.tonicEndSeconds())
                && context.tonicEndSeconds() > context.tonicStartSeconds();
        double start;
        double end;
        if (region) {
            start = context.tonicStartSeconds();
            end = context.tonicEndSeconds();
        } else {
            double lastVoiced = context.frames().stream().filter(PitchFrame::voiced)
                    .mapToDouble(PitchFrame::timeSeconds).max().orElse(context.audio().durationSeconds());
            start = Math.max(0.0, lastVoiced - 1.0);
            end = lastVoiced;
        }
        List<PitchFrame> evidence = context.frames().stream().filter(PitchFrame::voiced)
                .filter(value -> value.timeSeconds() >= start && value.timeSeconds() <= end)
                .filter(value -> value.confidence() >= .35).toList();
        double spread = robustSpread(evidence);
        return new TonicSelection(context.tonicHertz(),
                region ? TonicSelection.Source.GRAPH_REGION : TonicSelection.Source.AUTOMATIC_FINAL_NOTE,
                start, end, context.tonicConfidence(), spread, evidence.size(),
                context.audio().channelDescription(), true);
    }

    private static AcademicStudyDocument adapt(MakamStudyContext context, SinglePitchStudyResult study,
                                                TheoryCatalog theory,
                                                TheoryCatalog.Makam makam,
                                                FirstFourDirectionalAnalyzer.Result directionalProfiles) {
        Set<Integer> stableIndexes = new HashSet<>();
        study.stableTargetEvents().forEach(value -> stableIndexes.add(value.index()));
        List<AcademicStudyDocument.Event> events = study.allTargetContacts().stream()
                .map(value -> event(value, stableIndexes.contains(value.index()))).toList();
        List<AcademicStudyDocument.ProfileComparison> profiles = profileRows(study.profileComparison());
        TonicSelection tonic = study.tonicSelection();
        List<String> limitations = new ArrayList<>();
        limitations.add(t("report.limit.polyphonic",
                "In polyphonic choir or instrumental textures, the result represents the dominant melodic F0 track in the selected channel and cannot be attributed directly to one performer.",
                "Çok sesli koro veya çalgı dokusunda sonuç, seçilen kanaldaki baskın melodik F0 izini temsil eder; tekil bir icracıya doğrudan atfedilemez."));
        limitations.add(t("report.limit.lowConfidence",
                "Brief and low-confidence contacts are retained for audit but excluded from the primary statistical summaries.",
                "Kısa süreli ve düşük güvenli temaslar denetim amacıyla korunmuş, birincil istatistiksel özetlerden dışlanmıştır."));
        limitations.add(t("report.limit.profileNotDiagnosisDetailed",
                "Tonic-aligned interval-profile similarity is not automatic makam diagnosis; evaluate it together with melodic course, dominant, çeşni, modulation and cadence evidence.",
                "Karara hizalı aralık profili benzerliği otomatik makam teşhisi değildir; seyir, güçlü, çeşni, geçki ve kalış kanıtlarıyla birlikte değerlendirilmelidir."));
        limitations.add(study.profileComparison().evidenceSummary());
        return new AcademicStudyDocument(
                t("report.title.singlePitchDocument", "MakamLab — Academic Single-Pitch Performance Analysis",
                        "MakamLab — Tek Perde Akademik İcra Analizi"),
                new AcademicStudyDocument.Source(study.sourceName(), study.sourcePath(),
                        study.sourceSha256(), study.sourceDurationSeconds(), study.sourceSampleRate(),
                        study.analyzedChannel(), study.applicationVersion(), ENGINE_VERSION),
                new AcademicStudyDocument.Theory(study.makamName(), study.tonicNote(),
                        study.targetNoteName(), study.tonicSelection().hertz(),
                        study.theoreticalTargetCent(), study.theoreticalTargetHc(),
                        study.theoreticalTargetHertz(), study.profileComparison().referenceSystem() + " / 53-Hc",
                        theoryPitches(theory, makam, study.tonicSelection().hertz())),
                new AcademicStudyDocument.TonicEvidence(tonic.source().label(), tonic.hertz(),
                        tonic.startSeconds(), tonic.endSeconds(), tonic.confidence(),
                        tonic.robustSpreadCent(), tonic.voicedFrameCount(), tonic.userConfirmed(),
                        context.tonicMethod()),
                events, tonicTrace(context.frames(), tonic), profiles,
                List.of(
                t("report.method.f0Windows", "F0 was estimated with 40 ms analysis windows and steps of approximately 20 ms.",
                        "F0 kestirimi 40 ms uzunluğundaki analiz pencereleri ve yaklaşık 20 ms'lik adımlarla gerçekleştirilmiştir."),
                t("report.method.workingCopy", "The recording was analyzed from an anti-alias-filtered 22.05 kHz working copy.",
                        "Ses kaydı, anti-alias süzgeç uygulanarak 22,05 kHz örnekleme hızında hazırlanan çalışma kopyası üzerinden çözümlenmiştir."),
                t("report.method.canonicalAssignment", "Canonical pitch events were assigned to mutually exclusive 53-Hc theoretical centres.",
                        "Kanonik perde olayları, 53-Hc kuram merkezlerine karşılıklı dışlayan biçimde atanmıştır."),
                t("report.method.robustMedian", "To reduce transition effects, each event's representative value was computed from the robust median of its middle body; entry, settled and exit values are also reported.",
                        "Her olayın temsilî değeri, geçiş etkisini azaltmak amacıyla olay gövdesinin orta bölümündeki sağlam medyan üzerinden hesaplanmış; giriş, yerleşik ve çıkış değerleri ayrıca raporlanmıştır."),
                t("report.method.confidenceAudit", "Candidates with 35–65% confidence were retained in the visual audit layer but excluded from the primary descriptive statistics.",
                        "Güveni yüzde 35–65 aralığında kalan adaylar görsel denetim katmanında korunmuş, ancak birincil betimsel istatistiklere dâhil edilmemiştir.")),
                limitations, ZonedDateTime.now(), List.of(),
                List.of(),
                firstFourInventoryPeaks(study.profileComparison()),
                firstFourPhraseEvidence(study.profileComparison(),
                        CadenceSimilarity.analyze(context.audio(), study.allCanonicalEvents(),
                                tonic.hertz(), makam.name(), study.profileComparison())));
    }

    private static AcademicStudyDocument adaptInterval(MakamStudyContext context,
                                                        PitchIntervalStudyResult study,
                                                        TheoryCatalog theory,
                                                        TheoryCatalog.Makam makam,
                                                        FirstFourDirectionalAnalyzer.Result directionalProfiles) {
        Set<Integer> stableIndexes = new HashSet<>();
        study.stableIntervalEvents().forEach(value -> stableIndexes.add(value.index()));
        List<AcademicStudyDocument.Event> events = study.intervalContacts().stream()
                .map(value -> intervalEvent(value, study.config(),
                        stableIndexes.contains(value.index()))).toList();
        List<AcademicStudyDocument.ProfileComparison> profiles = profileRows(study.profileComparison());
        TonicSelection tonic = study.tonicSelection();
        double midpointCent = (study.lowerBoundaryCent() + study.upperBoundaryCent()) / 2.0;
        double midpointHz = tonic.hertz() * Math.pow(2.0, midpointCent / 1200.0);
        String intervalName = t("report.intervalName", "{0}–{1} interval (boundaries excluded)",
                "{0}–{1} arası (sınırlar hariç)", study.lowerBoundaryName(), study.upperBoundaryName());
        List<AcademicStudyDocument.TheoryPitch> pitchCenters =
                theoryPitches(theory, makam, study.tonicSelection().hertz());
        List<AcademicStudyDocument.TheoryPitch> expectedReferences = expectedReferencePitches(
                study.profileComparison(), study.lowerBoundaryCent(), study.upperBoundaryCent(),
                pitchCenters, study.tonicSelection().hertz());
        List<String> method = List.of(
                t("report.method.intervalOpen", "The study set is defined by the open-interval condition lower boundary < observed F0 < upper boundary.",
                        "İnceleme kümesi, alt sınır < gözlenen F0 < üst sınır biçimindeki açık aralık koşuluyla tanımlanmıştır."),
                t("report.method.boundariesExcluded", "Events assigned to the lower and upper boundary pitches are excluded regardless of performance deviation.",
                        "Alt ve üst sınır perdelerine atanan olaylar, icra sapmalarından bağımsız olarak araştırma dışında bırakılmıştır."),
                t("report.method.nearest53Hc", "For each event in the open interval, the nearest canonical 53-Hc pitch centre is determined and its signed C/Hc distance is calculated.",
                        "Açık aralıkta kalan her olay için en yakın kanonik 53-Hc perde merkezi belirlenmiş ve bu merkeze göre imzalı C/Hc uzaklığı hesaplanmıştır."),
                t("report.method.unnamedIncluded", "Intermediate sonorities beyond the naming threshold are retained as Unnamed and reported with their distance to the nearest theoretical pitch.",
                        "Adlandırma eşiğini aşan ara seslendirmeler veri kaybını önlemek amacıyla dışlanmamış; en yakın kuramsal perdeye olan uzaklıklarıyla birlikte 'Adlandırılamayan' kategorisinde değerlendirilmiştir."),
                t("report.method.f0Windows", "F0 was estimated with 40 ms analysis windows and steps of approximately 20 ms.",
                        "F0 kestirimi 40 ms uzunluğundaki analiz pencereleri ve yaklaşık 20 ms'lik adımlarla gerçekleştirilmiştir."),
                t("report.method.workingCopy", "The recording was analyzed from an anti-alias-filtered 22.05 kHz working copy.",
                        "Ses kaydı, anti-alias süzgeç uygulanarak 22,05 kHz örnekleme hızında hazırlanan çalışma kopyası üzerinden çözümlenmiştir."),
                t("report.method.confidenceAudit", "Candidates with 35–65% confidence were retained in the visual audit layer but excluded from the primary descriptive statistics.",
                        "Güveni yüzde 35–65 aralığında kalan adaylar görsel denetim katmanında korunmuş, ancak birincil betimsel istatistiklere dâhil edilmemiştir."));
        List<String> limitations = List.of(
                t("report.limit.polyphonic", "In polyphonic choir or instrumental textures, the result represents the dominant melodic F0 track in the selected channel and cannot be attributed directly to one performer.",
                        "Çok sesli koro veya çalgı dokusunda sonuç, seçilen kanaldaki baskın melodik F0 izini temsil eder; tekil bir icracıya doğrudan atfedilemez."),
                t("report.limit.boundaries", "Because boundary pitches are intentionally excluded, this report does not describe their own performance deviations.",
                        "Sınır perdeleri bilinçli olarak dışlandığından bu rapor alt ve üst sınırların kendi icra sapmalarını açıklamaz."),
                t("report.limit.lowConfidence",
                        "Brief and low-confidence contacts are retained for audit but excluded from the primary statistical summaries.",
                        "Kısa ve düşük güvenli temaslar denetim amacıyla korunur; birincil istatistiksel özetlere alınmaz."),
                t("report.limit.profileNotDiagnosisShort", "Tonic-aligned interval-profile similarity is not automatic makam diagnosis; melodic course and çeşni must also be evaluated.",
                        "Karara hizalı aralık profili benzerliği otomatik makam teşhisi değildir; seyir ve çeşni ayrıca değerlendirilmelidir."),
                study.profileComparison().evidenceSummary());
        return new AcademicStudyDocument(
                t("report.title.intervalDocument", "MakamLab — Between-Pitches Analysis",
                        "MakamLab — Perdeler Arası Analiz"),
                new AcademicStudyDocument.Source(study.sourceName(), study.sourcePath(),
                        study.sourceSha256(), study.sourceDurationSeconds(), study.sourceSampleRate(),
                        study.analyzedChannel(), study.applicationVersion(), ENGINE_VERSION),
                new AcademicStudyDocument.Theory(study.makamName(), study.tonicNote(),
                        intervalName, tonic.hertz(), midpointCent,
                        midpointCent / AnalysisConfig.COMMA_CENT, midpointHz,
                        study.profileComparison().referenceSystem() + " / 53-Hc",
                        t("report.analysisType.interval", "Between-pitches analysis", "Perdeler arası analiz"),
                        study.lowerBoundaryName(), study.lowerBoundaryCent(),
                        study.lowerBoundaryHc(), study.lowerBoundaryHertz(),
                        study.upperBoundaryName(), study.upperBoundaryCent(),
                        study.upperBoundaryHc(), study.upperBoundaryHertz(), false,
                        pitchCenters, expectedReferences),
                new AcademicStudyDocument.TonicEvidence(tonic.source().label(), tonic.hertz(),
                        tonic.startSeconds(), tonic.endSeconds(), tonic.confidence(),
                        tonic.robustSpreadCent(), tonic.voicedFrameCount(), tonic.userConfirmed(),
                        context.tonicMethod()),
                events, tonicTrace(context.frames(), tonic), profiles,
                method, limitations, ZonedDateTime.now(), List.of(),
                List.of(),
                firstFourInventoryPeaks(study.profileComparison()),
                firstFourPhraseEvidence(study.profileComparison(),
                        CadenceSimilarity.analyze(context.audio(), study.allCanonicalEvents(),
                                tonic.hertz(), makam.name(), study.profileComparison())));
    }

    private static List<AcademicStudyDocument.DirectionalFirstFourProfile> directionalFirstFourRows(
            FirstFourDirectionalAnalyzer.Result result) {
        List<AcademicStudyDocument.DirectionalFirstFourProfile> rows = new ArrayList<>();
        result.ascending().forEach(value -> rows.add(directionalFirstFourRow("Çıkıcı", value)));
        result.descending().forEach(value -> rows.add(directionalFirstFourRow("İnici", value)));
        return List.copyOf(rows);
    }

    private static AcademicStudyDocument.DirectionalFirstFourProfile directionalFirstFourRow(
            String direction, FirstFourDirectionalAnalyzer.Candidate value) {
        return new AcademicStudyDocument.DirectionalFirstFourProfile(direction, value.rank(),
                value.profileName(), value.phraseCount(), value.evidenceEventCount(),
                value.timeRegionCount(), value.patternCounts(), value.evidenceStatus().name(),
                value.scorePercent(), value.coveragePercent(),
                value.medianAbsoluteErrorCent(), value.p90AbsoluteErrorCent(),
                value.meanSignedErrorCent());
    }

    private static List<AcademicStudyDocument.TheoryPitch> theoryPitches(
            TheoryCatalog theory, TheoryCatalog.Makam makam, double tonicHertz) {
        return theory.notesForPitchAnalysis(makam).stream().map(note -> {
            double cent = theory.relativeCent(makam, note);
            return new AcademicStudyDocument.TheoryPitch(note.toString(), cent,
                    cent / AnalysisConfig.COMMA_CENT,
                    tonicHertz * Math.pow(2.0, cent / 1200.0));
        }).toList();
    }

    /**
     * Finds the selected makam profile's theoretical degrees that fall inside
     * the user's open pitch interval.  These are report references, not a
     * diagnosis inferred from the recording.  Register shifts are considered
     * so an interval above or below the base octave still receives the correct
     * named reference pitch.
     */
    static List<AcademicStudyDocument.TheoryPitch> expectedReferencePitches(
            MakamProfileComparison comparison, double firstBoundaryCent,
            double secondBoundaryCent, List<AcademicStudyDocument.TheoryPitch> pitchCenters,
            double tonicHertz) {
        MakamProfileComparison.Candidate selected = comparison.candidates().stream()
                .filter(MakamProfileComparison.Candidate::selectedProfile)
                .findFirst().orElse(null);
        if (selected == null) return List.of();
        double lower = Math.min(firstBoundaryCent, secondBoundaryCent);
        double upper = Math.max(firstBoundaryCent, secondBoundaryCent);
        List<AcademicStudyDocument.TheoryPitch> references = new ArrayList<>();
        for (MakamProfileComparison.DegreeMatch degree : selected.degreeMatches()) {
            double baseCent = degree.theoreticalCent();
            if (!Double.isFinite(baseCent)) continue;
            int firstOctave = (int) Math.floor((lower - baseCent) / 1200.0) - 1;
            int lastOctave = (int) Math.ceil((upper - baseCent) / 1200.0) + 1;
            for (int octave = firstOctave; octave <= lastOctave; octave++) {
                double cent = baseCent + octave * 1200.0;
                if (!(cent > lower + 1.0e-6 && cent < upper - 1.0e-6)) continue;
                boolean duplicate = references.stream()
                        .anyMatch(value -> Math.abs(value.relativeCent() - cent) < .05);
                if (duplicate) continue;
                AcademicStudyDocument.TheoryPitch nearest = pitchCenters.stream()
                        .min(java.util.Comparator.comparingDouble(value ->
                                Math.abs(value.relativeCent() - cent)))
                        .orElse(null);
                String name = nearest != null
                        && Math.abs(nearest.relativeCent() - cent) <= AnalysisConfig.COMMA_CENT * .55
                        ? nearest.noteName() : t("report.theoreticalDegreeName",
                        "Theoretical degree {0}", "Kuramsal {0}. derece", degree.degree());
                references.add(new AcademicStudyDocument.TheoryPitch(name, cent,
                        cent / AnalysisConfig.COMMA_CENT,
                        tonicHertz * Math.pow(2.0, cent / 1200.0)));
            }
        }
        references.sort(java.util.Comparator.comparingDouble(
                AcademicStudyDocument.TheoryPitch::relativeCent));
        return List.copyOf(references);
    }

    private static AcademicStudyDocument.Event event(StudyPitchEvent value, boolean stable) {
        return new AcademicStudyDocument.Event(value.index(), value.noteName(),
                value.startSeconds(), value.endSeconds(),
                value.observedHertz(), value.observedRelativeCent(), value.theoreticalRelativeCent(),
                value.deviationCent(), value.entryDeviationCent(), value.settledDeviationCent(),
                value.exitDeviationCent(), value.stabilityCent(), value.confidence(),
                value.direction().label(), value.eventType().label(), value.measurable(), stable,
                true, "");
    }

    private static AcademicStudyDocument.Event intervalEvent(StudyPitchEvent value,
                                                               AnalysisConfig config,
                                                               boolean stable) {
        String displayName = IntervalPitchNaming.displayName(value, config);
        String nearest = IntervalPitchNaming.isCanonicallyNamed(value, config) ? ""
                : IntervalPitchNaming.nearestDistanceLabel(value.noteName(), value.deviationCent());
        return new AcademicStudyDocument.Event(value.index(), displayName,
                value.startSeconds(), value.endSeconds(), value.observedHertz(),
                value.observedRelativeCent(), value.theoreticalRelativeCent(),
                value.deviationCent(), value.entryDeviationCent(), value.settledDeviationCent(),
                value.exitDeviationCent(), value.stabilityCent(), value.confidence(),
                value.direction().label(), value.eventType().label(),
                IntervalEventTier.statisticallyMeasurable(value), stable,
                true, nearest);
    }

    static List<AcademicStudyDocument.ProfileComparison> profileRows(MakamProfileComparison comparison) {
        if (comparison == null) return List.of();
        Map<String, LinkedHashSet<String>> firstFourGroups = new LinkedHashMap<>();
        for (MakamProfileComparison.Candidate candidate : comparison.candidates()) {
            String signature = firstFourSignature(candidate.degreeMatches());
            LinkedHashSet<String> names = firstFourGroups.computeIfAbsent(signature,
                    ignored -> new LinkedHashSet<>());
            if (candidate.indistinguishableProfiles().isEmpty()) names.add(candidate.makamName());
            else names.addAll(candidate.indistinguishableProfiles());
        }

        // The user-facing report works with four canonical interval families.  The
        // tuning-system catalogue may contain several more specific makam profiles
        // inside one family (for example Acem, Nühüft and Gülizar inside Uşşak).
        // Keep those profiles as evidence, but do not let them occupy several rows
        // in the principal ranking table.
        Map<String, FullProfileGroup> fullGroups = new LinkedHashMap<>();
        for (MakamProfileComparison.Candidate candidate : comparison.candidates()) {
            LinkedHashSet<String> candidateNames = candidateNames(candidate);
            java.util.Optional<String> canonical = FirstFourMakamGroupCatalog.canonicalLabel(
                    List.of(), candidateNames);
            String key = canonical.map(value -> "canonical:" + value)
                    .orElse("candidate:" + candidate.rank());
            FullProfileGroup group = fullGroups.computeIfAbsent(key,
                    ignored -> new FullProfileGroup(canonical.orElse(""), new ArrayList<>(),
                            new LinkedHashSet<>()));
            group.candidates().add(candidate);
            group.memberNames().addAll(candidateNames);
        }

        List<FullProfileGroup> orderedGroups = fullGroups.values().stream()
                .sorted(java.util.Comparator.comparingInt(FullProfileGroup::bestOriginalRank))
                .toList();
        List<AcademicStudyDocument.ProfileComparison> rows = new ArrayList<>(orderedGroups.size());
        int displayRank = 1;
        for (FullProfileGroup group : orderedGroups) {
            MakamProfileComparison.Candidate candidate = group.representative();
            boolean canonicalGroup = !group.canonicalLabel().isBlank();
            // Canonical family membership does not mean that every member has an
            // identical complete profile.  Keep exact indistinguishability separate
            // from the requested user-facing family collapse.
            boolean indistinguishable = !canonicalGroup
                    && candidate.indistinguishableProfiles().size() > 1;
            String verdict;
            if (!comparison.sufficientEvidence()) verdict = comparison.evidenceSummary();
            else if (canonicalGroup) {
                verdict = group.canonicalLabel() + " grubu — en iyi eşleşen temsilci: "
                        + candidate.makamName() + "; birlikte değerlendirilen üyeler: "
                        + String.join(", ", group.memberNames())
                        + (candidate.indistinguishableProfiles().size() > 1
                        ? "; temsilciyle aynı tam aralık profili: "
                        + String.join(", ", candidate.indistinguishableProfiles())
                        : "")
                        + (group.containsSelectedProfile()
                        ? ". Kullanıcının seçtiği makam bu gruptadır."
                        : ".");
            }
            else if (indistinguishable) verdict = "Aynı aralık profili: "
                    + String.join(", ", candidate.indistinguishableProfiles());
            else if (candidate.selectedProfile()) verdict = "Kullanıcının seçtiği makam profili";
            else verdict = "Karara hizalı aralık benzerliği";
            List<AcademicStudyDocument.DegreeComparison> firstFour = candidate.degreeMatches().stream()
                    .limit(4).map(value -> {
                        if (value.degree() == 1) {
                            // The tonic is the user-confirmed reference itself.  It is an anchor,
                            // not a second independently detected pitch that may penalize every profile.
                            return new AcademicStudyDocument.DegreeComparison(1,
                                    value.theoreticalCent(), value.theoreticalHc(), true,
                                    0.0, 0.0, 0.0, 0.0, 0, 0.0);
                        }
                        return new AcademicStudyDocument.DegreeComparison(
                                value.degree(), value.theoreticalCent(), value.theoreticalHc(),
                                value.observed(), value.observedCent(), value.observedHc(),
                                value.residualCent(), value.residualHc(),
                                value.observed() ? value.eventCount() : 0,
                                value.observed() ? value.totalDurationSeconds() : 0.0);
                    }).toList();
            int observedCount = (int) firstFour.stream()
                    .filter(AcademicStudyDocument.DegreeComparison::observed).count();
            // Degree 1 is the fixed anchor shared by every profile.  Only the
            // discriminating degrees 2-4 contribute to the similarity score.
            List<AcademicStudyDocument.DegreeComparison> discriminating = firstFour.stream()
                    .filter(value -> value.degree() > 1).toList();
            // The comparator has already scored every profile against the same
            // immutable observed centres for degrees 2, 3 and 4.  Do not run a
            // second report-only algorithm that can reassign clusters and
            // produce a percentage different from the analysis result.
            double firstFourScore = candidate.scorePercent();
            String signature = firstFourSignature(candidate.degreeMatches());
            rows.add(new AcademicStudyDocument.ProfileComparison(displayRank++,
                    canonicalGroup ? group.canonicalLabel() : candidate.makamName(),
                    candidate.scorePercent(), candidate.medianAbsoluteErrorCent(),
                    candidate.p90AbsoluteErrorCent(), candidate.withinOneHcWeightPercent(),
                    candidate.degreeCoveragePercent(), comparison.acceptedEventCount(), verdict,
                    indistinguishable, firstFourScore, observedCount, firstFour,
                    comparison.sufficientEvidence(), comparison.evidenceSummary(), signature,
                    List.copyOf(firstFourGroups.getOrDefault(signature, new LinkedHashSet<>()))));
        }
        return List.copyOf(rows);
    }

    /**
     * Preserve every global first-four-domain pitch mode for audit.  This is an
     * inventory layer, not the phrase evidence used to rank tetrachords: no two
     * modes are averaged together and no reference profile may select its own
     * preferred subset.
     */
    private static List<AcademicStudyDocument.FirstFourPeak> firstFourInventoryPeaks(
            MakamProfileComparison comparison) {
        if (comparison == null || comparison.observedClusters().isEmpty()) return List.of();

        Map<Integer, List<Double>> theoreticalByDegree = new LinkedHashMap<>();
        comparison.candidates().forEach(candidate -> candidate.degreeMatches().stream()
                .filter(match -> match.degree() >= 1 && match.degree() <= 4)
                .forEach(match -> theoreticalByDegree
                        .computeIfAbsent(match.degree(), ignored -> new ArrayList<>())
                        .add(match.theoreticalCent())));

        record Assigned(MakamProfileComparison.ObservedCluster cluster, int degree) { }
        List<Assigned> assigned = comparison.observedClusters().stream()
                .map(cluster -> new Assigned(cluster,
                        nearestDegree(cluster.centerCent(), theoreticalByDegree)))
                .toList();

        Map<Integer, MakamProfileComparison.ObservedCluster> dominant = new LinkedHashMap<>();
        assigned.forEach(value -> dominant.merge(value.degree(), value.cluster(),
                (first, second) -> second.evidenceWeight() > first.evidenceWeight()
                        ? second : first));

        return assigned.stream()
                .sorted(java.util.Comparator.comparingInt(Assigned::degree)
                        .thenComparingDouble(value -> value.cluster().centerCent()))
                .map(value -> {
                    MakamProfileComparison.ObservedCluster peak = value.cluster();
                    return new AcademicStudyDocument.FirstFourPeak(value.degree(),
                            peak.centerCent(), peak.centerHc(), peak.eventCount(),
                            peak.totalDurationSeconds(), peak.evidenceWeight(),
                            peak.meanConfidence(), dominant.get(value.degree()) == peak);
                }).toList();
    }

    private static int nearestDegree(double centerCent, Map<Integer, List<Double>> theoreticalByDegree) {
        int nearest = 0;
        double best = Double.POSITIVE_INFINITY;
        for (Map.Entry<Integer, List<Double>> entry : theoreticalByDegree.entrySet()) {
            for (double theoretical : entry.getValue()) {
                double distance = Math.abs(centerCent - theoretical);
                if (distance < best) {
                    best = distance;
                    nearest = entry.getKey();
                }
            }
        }
        return nearest;
    }

    private static AcademicStudyDocument.FirstFourPhraseEvidence firstFourPhraseEvidence(
            MakamProfileComparison comparison, CadenceSimilarity.Result cadence) {
        if (comparison == null || comparison.phraseEvidence() == null) {
            return AcademicStudyDocument.FirstFourPhraseEvidence.EMPTY;
        }
        MakamProfileComparison.PhraseEvidence evidence = comparison.phraseEvidence();
        List<AcademicStudyDocument.FirstFourPeak> peaks = evidence.degreePeaks().stream()
                .map(peak -> new AcademicStudyDocument.FirstFourPeak(peak.degree(),
                        peak.centerCent(), peak.centerHc(), peak.eventCount(),
                        peak.totalDurationSeconds(), peak.evidenceWeight(),
                        peak.meanConfidence(), peak.dominant()))
                .toList();
        return new AcademicStudyDocument.FirstFourPhraseEvidence(evidence.status().name(),
                evidence.qualifyingPhraseCount(), evidence.completePhraseCount(),
                evidence.partialPhraseCount(), evidence.evidenceEventCount(), peaks, cadence);
    }

    private static LinkedHashSet<String> candidateNames(MakamProfileComparison.Candidate candidate) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (candidate.indistinguishableProfiles().isEmpty()) names.add(candidate.makamName());
        else names.addAll(candidate.indistinguishableProfiles());
        names.add(candidate.makamName());
        return names;
    }

    private record FullProfileGroup(String canonicalLabel,
                                    List<MakamProfileComparison.Candidate> candidates,
                                    LinkedHashSet<String> memberNames) {
        private MakamProfileComparison.Candidate representative() {
            return candidates.stream().min(java.util.Comparator.comparingInt(
                    MakamProfileComparison.Candidate::rank)).orElseThrow();
        }

        private int bestOriginalRank() { return representative().rank(); }

        private boolean containsSelectedProfile() {
            return candidates.stream().anyMatch(MakamProfileComparison.Candidate::selectedProfile);
        }
    }

    private static String firstFourSignature(List<MakamProfileComparison.DegreeMatch> matches) {
        return matches.stream().limit(4)
                .map(value -> Long.toString(Math.round(value.theoreticalCent() * 1000.0)))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static List<AcademicStudyDocument.TracePoint> tonicTrace(List<PitchFrame> frames,
                                                                     TonicSelection tonic) {
        double center = (tonic.startSeconds() + tonic.endSeconds()) / 2.0;
        double start = Math.max(0.0, Math.min(tonic.startSeconds() - 5.0, center - 6.0));
        double end = Math.max(tonic.endSeconds() + 5.0, center + 6.0);
        List<PitchFrame> selected = frames.stream().filter(PitchFrame::voiced)
                .filter(value -> value.timeSeconds() >= start && value.timeSeconds() <= end).toList();
        int step = Math.max(1, selected.size() / 800);
        List<AcademicStudyDocument.TracePoint> result = new ArrayList<>();
        for (int i = 0; i < selected.size(); i += step) {
            PitchFrame frame = selected.get(i);
            boolean accepted = frame.timeSeconds() >= tonic.startSeconds()
                    && frame.timeSeconds() <= tonic.endSeconds()
                    && Math.abs(frame.absoluteCent() - hertzToCent(tonic.hertz())) <= 45.0;
            result.add(new AcademicStudyDocument.TracePoint(frame.timeSeconds(), frame.hertz(),
                    frame.confidence(), accepted));
        }
        return List.copyOf(result);
    }

    private static double robustSpread(List<PitchFrame> frames) {
        if (frames.isEmpty()) return Double.NaN;
        double[] values = frames.stream().mapToDouble(PitchFrame::absoluteCent).sorted().toArray();
        double median = median(values);
        double[] deviations = Arrays.stream(values).map(value -> Math.abs(value - median)).sorted().toArray();
        return median(deviations) * 1.4826;
    }

    private static double median(double[] sorted) {
        if (sorted.length == 0) return Double.NaN;
        return sorted.length % 2 == 0
                ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0
                : sorted[sorted.length / 2];
    }

    private static double hertzToCent(double hertz) {
        return 1200.0 * Math.log(hertz / 16.351597831287414) / Math.log(2.0);
    }

    private static String safeStem(String value) {
        String stem = value == null ? "MakamLab-Report" : value.replaceFirst("\\.[^.]+$", "");
        stem = stem.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ").strip();
        return stem.isBlank() ? "MakamLab-Report" : stem;
    }

    /**
     * Opens a successfully written report with the operating system's default
     * DOCX application.  Opening is deliberately best-effort: the report has
     * already been saved, so a missing desktop integration must never turn a
     * successful export into an export failure.
     */
    static boolean openSavedReport(Path path) {
        if (path == null || !Files.isRegularFile(path) || !Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) return false;
            desktop.open(path.toFile());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void showLocalizedMessage(Window owner, Object message, String title, int messageType) {
        JOptionPane.showOptionDialog(owner, message, title,
                JOptionPane.DEFAULT_OPTION, messageType, null,
                new Object[]{ReportLocalization.text("common.close", "Close", "Kapat")}, null);
    }

    private static String t(String key, String english, String turkish, Object... arguments) {
        return ReportLocalization.text(key, english, turkish, arguments);
    }
}
