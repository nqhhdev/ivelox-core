package com.ivelox.core.health;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Educational body atlas — not medical advice. */
@Service
public class BodyAtlasService {

    public Map<String, Object> atlas() {
        return Map.of(
                "disclaimer",
                "Educational estimates only — not a diagnosis or treatment plan. "
                        + "Discuss personal health decisions with a qualified clinician.",
                "citations", List.of(
                        Map.of(
                                "id", "who-bmi",
                                "title", "WHO BMI classification (adults)",
                                "url", "https://www.who.int/news-room/fact-sheets/detail/obesity-and-overweight"
                        ),
                        Map.of(
                                "id", "deurenberg",
                                "title", "Deurenberg et al. body-fat % from BMI (estimate)",
                                "url", "https://pubmed.ncbi.nlm.nih.gov/2041279/"
                        ),
                        Map.of(
                                "id", "aha-activity",
                                "title", "AHA recommendations for physical activity",
                                "url", "https://www.heart.org/en/healthy-living/fitness/fitness-basics/aha-recs-for-physical-activity-in-adults"
                        ),
                        Map.of(
                                "id", "iof-bone",
                                "title", "International Osteoporosis Foundation — bone health basics",
                                "url", "https://www.osteoporosis.foundation/patients/about-osteoporosis"
                        ),
                        Map.of(
                                "id", "who-salt",
                                "title", "WHO sodium reduction (vascular risk framing)",
                                "url", "https://www.who.int/news-room/fact-sheets/detail/salt-reduction"
                        )
                ),
                "layers", List.of(
                        Map.of("id", "fat", "label", "Fat", "color", "#c4a35a"),
                        Map.of("id", "vessels", "label", "Vessels", "color", "#c45c5c"),
                        Map.of("id", "bone", "label", "Bone", "color", "#d8d2c4")
                ),
                "regions", List.of(
                        region("head", "Head / neck", List.of(0, 1.35, 0),
                                tip("fat", "Neck circumference correlates with upper-body adiposity in some screening tools; keep overall energy balance.", List.of("who-bmi")),
                                tip("vessels", "Carotid health benefits from not smoking, blood-pressure control, and regular aerobic activity (AHA framing).", List.of("aha-activity")),
                                tip("bone", "Jaw/skull density follows systemic bone health — adequate protein, calcium-rich foods, vitamin D, and impact loading matter (IOF).", List.of("iof-bone"))
                        ),
                        region("chest", "Chest / heart zone", List.of(0, 0.85, 0),
                                tip("fat", "Central fat is more metabolically active than peripheral fat; waist-focused habits matter more than scale weight alone.", List.of("who-bmi")),
                                tip("vessels", "Coronary risk is lowered by activity, not smoking, and dietary patterns lower in excess sodium and ultra-processed foods (AHA/WHO framing).", List.of("aha-activity", "who-salt")),
                                tip("bone", "Rib cage protects organs; overall bone mass still depends on resistance training and nutrition.", List.of("iof-bone"))
                        ),
                        region("abdomen", "Abdomen", List.of(0, 0.35, 0),
                                tip("fat", "Abdominal adiposity links to cardiometabolic risk. BMI is a screening tool — combine with waist trends when possible (WHO).", List.of("who-bmi")),
                                tip("vessels", "Visceral fat and inactivity associate with higher vascular risk; progressive walking/zone-2 work helps (AHA activity guidance).", List.of("aha-activity")),
                                tip("bone", "Spine loading (safe strength work) supports vertebral bone; avoid crash diets that undercut protein.", List.of("iof-bone"))
                        ),
                        region("arms", "Arms", List.of(0.55, 0.7, 0),
                                tip("fat", "Limb fat is less metabolically risky than deep abdominal fat, but total energy still counts.", List.of("who-bmi")),
                                tip("vessels", "Peripheral circulation improves with consistent movement and not smoking.", List.of("aha-activity")),
                                tip("bone", "Upper-body strength training loads arm bones and supports functional independence (IOF activity framing).", List.of("iof-bone"))
                        ),
                        region("legs", "Legs / hips", List.of(0, -0.35, 0),
                                tip("fat", "Gluteofemoral fat is often less harmful than visceral fat; still track overall calorie balance for weight goals.", List.of("who-bmi")),
                                tip("vessels", "Leg muscle pump supports venous return — walking after meals is a simple habit.", List.of("aha-activity")),
                                tip("bone", "Hip and femur benefit from weight-bearing activity; osteoporosis risk rises with aging, low BMI extremes, and inactivity (IOF).", List.of("iof-bone"))
                        )
                ),
                "future_conditions", List.of(
                        Map.of(
                                "id", "dyslipidemia",
                                "label", "Dyslipidemia (blood lipids)",
                                "systems", List.of("fat", "vessels"),
                                "status", "planned",
                                "note", "Will drive prefer/avoid food rules (e.g. limit ultra-processed, emphasize unsaturated fats) with cited guidelines."
                        ),
                        Map.of(
                                "id", "hypertension",
                                "label", "Hypertension",
                                "systems", List.of("vessels"),
                                "status", "planned",
                                "note", "Sodium awareness + DASH-style patterns (WHO salt reduction / dietary patterns)."
                        ),
                        Map.of(
                                "id", "osteopenia",
                                "label", "Low bone mass / osteopenia",
                                "systems", List.of("bone"),
                                "status", "planned",
                                "note", "Protein, calcium-rich foods, vitamin D, resistance loading; avoid chronic crash deficits."
                        )
                )
        );
    }

    private static Map<String, Object> region(String id, String label, List<Number> position, Map<String, Object>... tips) {
        return Map.of(
                "id", id,
                "label", label,
                "position", position,
                "tips", List.of(tips)
        );
    }

    private static Map<String, Object> tip(String layer, String text, List<String> citationIds) {
        return Map.of(
                "layer", layer,
                "text", text,
                "citation_ids", citationIds
        );
    }
}
