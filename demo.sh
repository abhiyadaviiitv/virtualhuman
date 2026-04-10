
ENDPOINT="http://localhost:8080/behavior/generate"
BOLD="\033[1m"
GREEN="\033[1;32m"
CYAN="\033[1;36m"
YELLOW="\033[1;33m"
MAGENTA="\033[1;35m"
RESET="\033[0m"

send_request() {
    local label=$1
    local file=$2
    local color=$3

    echo ""
    echo -e "${color}════════════════════════════════════════════════════════════${RESET}"
    echo -e "${color}  ${label}${RESET}"
    echo -e "${color}════════════════════════════════════════════════════════════${RESET}"
    echo ""
    echo -e "${BOLD}📤 Input payload key fields:${RESET}"

    # Show the important fields from the JSON
    echo -e "   Time:        $(cat "$file" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['worldState']['time'])" 2>/dev/null)"
    echo -e "   Activities:  $(cat "$file" | python3 -c "import sys,json; d=json.load(sys.stdin); print(', '.join(d['worldState']['completedActivities'][-3:]))" 2>/dev/null)"
    echo -e "   Scene:       $(cat "$file" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['worldState']['scene']['sceneDescription'])" 2>/dev/null)"
    echo -e "   Objects:     $(cat "$file" | python3 -c "import sys,json; d=json.load(sys.stdin); print(', '.join([o['name'] for o in d['worldState']['scene']['objects']]))" 2>/dev/null)"
    echo -e "   Neuroticism: $(cat "$file" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['worldState']['personality']['neuroticism'])" 2>/dev/null)"
    echo ""
    echo -e "${BOLD}📥 LLM Response:${RESET}"

    response=$(curl -s -X POST "$ENDPOINT" \
        -H "Content-Type: application/json" \
        -d @"$file")

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
    echo ""
}

echo ""
echo -e "${BOLD}🧠 Virtual Human Brain — Live Demonstration${RESET}"
echo -e "   Showing how the LLM adapts behavior based on changing inputs."
echo -e "   Each scenario sends a different JSON payload to the backend."
echo ""

# ────────────────────────────────────────────────
# SCENARIO 1: Morning — NPC just woke up
# Expected: The NPC should seek food/breakfast
# ────────────────────────────────────────────────
send_request \
    "SCENARIO 1: MORNING (08:00) — NPC just woke up, near the bed" \
    "demo_scenario_1_morning.json" \
    "$GREEN"

read -p "Press Enter for next scenario..."

# ────────────────────────────────────────────────
# SCENARIO 2: After Work — Same NPC, but 7 hours of coding done
# Expected: The NPC should seek rest/entertainment (burnout)
# ────────────────────────────────────────────────
send_request \
    "SCENARIO 2: AFTER WORK (18:00) — Same NPC, 7+ hours of work done" \
    "demo_scenario_2_after_work.json" \
    "$CYAN"

read -p "Press Enter for next scenario..."

# ────────────────────────────────────────────────
# SCENARIO 3: Different Scene — Same state as Scenario 2, but in a PARK
# Expected: Same need (rest), but different object selection (bench, not sofa)
# ────────────────────────────────────────────────
send_request \
    "SCENARIO 3: DIFFERENT SCENE — Same exhausted NPC, but now in a PARK" \
    "demo_scenario_3_different_scene.json" \
    "$YELLOW"

read -p "Press Enter for next scenario..."

# ────────────────────────────────────────────────
# SCENARIO 4: Different Personality — Highly neurotic, introverted accountant
# Expected: Anxiety-driven behavior, different style even with same scene
# ────────────────────────────────────────────────
send_request \
    "SCENARIO 4: DIFFERENT PERSONALITY — Same scene, but high-neurotic introvert" \
    "demo_scenario_4_high_neurotic.json" \
    "$MAGENTA"

echo ""
echo -e "${BOLD}✅ Demonstration Complete!${RESET}"
echo ""
echo "Key takeaways for the invigilator:"
echo "  1. Scenario 1 → 2:  Same NPC, different TIME + ACTIVITIES = different behavior"
echo "  2. Scenario 2 → 3:  Same state, different SCENE OBJECTS = different object choice"
echo "  3. Scenario 2 → 4:  Same scene, different PERSONALITY = different behavior style"
echo ""
