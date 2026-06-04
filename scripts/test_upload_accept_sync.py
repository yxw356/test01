import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND_SERVICE = ROOT / "src/main/java/com/yuki/enterprise_private_rag_qa/service/FileTypeValidationService.java"
FRONTEND_CONSTANTS = ROOT / "frontend/src/constants/common.ts"


class UploadAcceptSyncTest(unittest.TestCase):
    def test_frontend_accept_matches_backend_supported_extensions(self):
        backend_source = BACKEND_SERVICE.read_text(encoding="utf-8")
        frontend_source = FRONTEND_CONSTANTS.read_text(encoding="utf-8")

        backend_match = re.search(
            r"SUPPORTED_DOCUMENT_EXTENSIONS\s*=\s*new HashSet<>\(Arrays\.asList\((.*?)\)\);",
            backend_source,
            re.S,
        )
        self.assertIsNotNone(backend_match, "backend supported extension list not found")

        frontend_match = re.search(r"uploadAccept\s*=\s*'([^']+)'", frontend_source)
        self.assertIsNotNone(frontend_match, "frontend uploadAccept constant not found")

        backend_extensions = set(re.findall(r'"([a-z0-9]+)"', backend_match.group(1)))
        frontend_extensions = {
            item.strip().lstrip(".")
            for item in frontend_match.group(1).split(",")
            if item.strip()
        }

        self.assertEqual(backend_extensions, frontend_extensions)


if __name__ == "__main__":
    unittest.main()
