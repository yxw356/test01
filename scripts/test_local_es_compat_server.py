import importlib.util
import pathlib
import sys
import unittest


SCRIPT_PATH = pathlib.Path(__file__).with_name("local_es_compat_server.py")


def load_module():
    spec = importlib.util.spec_from_file_location("local_es_compat_server", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class LocalEsCompatServerTest(unittest.TestCase):
    def test_extracts_terms_only_from_text_content_match_query(self):
        module = load_module()
        request = {
            "query": {
                "bool": {
                    "must": [{"match": {"textContent": {"query": "差旅报销标准"}}}],
                    "filter": [{"term": {"knowledgeScope": {"value": "PUBLIC"}}}],
                }
            }
        }

        terms = module.extract_text_query_terms(request)

        self.assertIn("差旅报销标准", terms)
        self.assertIn("报销", terms)
        self.assertNotIn("knowledgeScope", terms)
        self.assertNotIn("PUBLIC", terms)

    def test_permission_filter_rejects_private_department_docs(self):
        module = load_module()
        permission_filter = {
            "bool": {
                "should": [
                    {"term": {"knowledgeScope": {"value": "PUBLIC"}}},
                    {"term": {"departmentId": {"value": "FIN"}}},
                ],
                "minimum_should_match": "1",
            }
        }

        self.assertTrue(module.matches_filter({"knowledgeScope": "PUBLIC"}, permission_filter))
        self.assertTrue(module.matches_filter({"knowledgeScope": "DEPARTMENT", "departmentId": "FIN"}, permission_filter))
        self.assertFalse(module.matches_filter({"knowledgeScope": "DEPARTMENT", "departmentId": "OPS"}, permission_filter))

    def test_scores_relevant_content_above_irrelevant_permission_matches(self):
        module = load_module()
        query_terms = ["差旅报销"]
        relevant = {"textContent": "差旅报销需要提交发票和审批单", "knowledgeScope": "PUBLIC"}
        irrelevant = {"textContent": "知识库系统使用说明", "knowledgeScope": "PUBLIC"}

        self.assertGreater(module.score_doc(relevant, query_terms), module.score_doc(irrelevant, query_terms))


if __name__ == "__main__":
    unittest.main()
