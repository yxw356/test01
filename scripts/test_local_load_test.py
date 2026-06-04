import importlib.util
import pathlib
import sys
import unittest


SCRIPT_PATH = pathlib.Path(__file__).with_name("local_load_test.py")


def load_module():
    spec = importlib.util.spec_from_file_location("local_load_test", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class LocalLoadTestHelpersTest(unittest.TestCase):
    def test_summarize_results_groups_by_scenario_and_reports_percentiles(self):
        module = load_module()
        results = [
            module.Sample("login", True, 10.0, None),
            module.Sample("login", True, 20.0, None),
            module.Sample("login", False, 30.0, "boom"),
            module.Sample("list", True, 5.0, None),
        ]

        summary = module.summarize_results(results)

        self.assertEqual(summary["overall"]["total"], 4)
        self.assertEqual(summary["overall"]["errors"], 1)
        self.assertEqual(summary["by_scenario"]["login"]["total"], 3)
        self.assertEqual(summary["by_scenario"]["login"]["errors"], 1)
        self.assertEqual(summary["by_scenario"]["login"]["p50_ms"], 20.0)
        self.assertEqual(summary["by_scenario"]["login"]["top_errors"][0]["message"], "boom")
        self.assertEqual(summary["by_scenario"]["list"]["p95_ms"], 5.0)

    def test_weighted_choice_prefers_only_positive_weights(self):
        module = load_module()
        choices = [("list", 0), ("upload", 0), ("chat", 1)]

        picked = {module.weighted_choice(choices) for _ in range(20)}

        self.assertEqual(picked, {"chat"})

    def test_chat_completion_detection_supports_first_byte_and_completion_modes(self):
        module = load_module()

        self.assertTrue(module.is_chat_satisfied({"chunk": "你好"}, "first-byte"))
        self.assertFalse(module.is_chat_satisfied({"chunk": "你好"}, "completion"))
        self.assertTrue(
            module.is_chat_satisfied({"type": "completion", "status": "finished"}, "completion")
        )


if __name__ == "__main__":
    unittest.main()
