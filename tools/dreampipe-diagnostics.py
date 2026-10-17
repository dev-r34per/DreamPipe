#!/usr/bin/env python3
"""Print complete failed-test and lint-error diagnostics from the current project."""

from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET


def main():
    print(f"Project: {Path.cwd()}")
    for module in ("common", "smarttubetv"):
        for report in sorted(Path(module, "build/test-results").glob("**/TEST-*.xml")):
            try:
                suite = ET.parse(report).getroot()
            except (ET.ParseError, OSError) as error:
                print(f"Could not read {report}: {error}")
                continue
            for case in suite.iter("testcase"):
                for failure in list(case.findall("failure")) + list(case.findall("error")):
                    print(f"\nFAILED TEST: {case.get('classname')} / {case.get('name')}")
                    print(f"Report: {report}")
                    print(failure.get("message", ""))
                    print(failure.text or "")

        reports = Path(module, "build/reports")
        parsed = set()
        for report in sorted(reports.glob("lint-results-*.xml")):
            try:
                root = ET.parse(report).getroot()
            except (ET.ParseError, OSError) as error:
                print(f"Could not read {report}: {error}")
                continue
            parsed.add(report.stem)
            issues = [issue for issue in root.findall("issue")
                      if issue.get("severity", "").lower() in ("fatal", "error")]
            counts = Counter(issue.get("id", "unknown") for issue in issues)
            print(f"\nLINT: {report} — {len(issues)} errors")
            print("By check: " + ", ".join(f"{key}={value}" for key, value in sorted(counts.items())))
            for issue in issues:
                print(f"\n[{issue.get('severity')}: {issue.get('id')}] {issue.get('message', '')}")
                for location in issue.findall("location"):
                    name = location.get("file", "")
                    line = int(location.get("line", "0"))
                    print(f"  {name}:{line}:{location.get('column', '0')}")
                    source = Path(name)
                    if not source.is_absolute() and not source.is_file():
                        source = Path(module, name)
                    if source.is_file() and line > 0:
                        lines = source.read_text(errors="replace").splitlines()
                        for index in range(max(0, line - 3), min(len(lines), line + 2)):
                            print(f"    {index + 1}: {lines[index]}")
        # Older lint configurations may only emit text. Keep it complete, including explanations.
        for report in sorted(reports.glob("lint-results-*.txt")):
            if report.stem not in parsed:
                print(f"\nFULL LINT REPORT: {report}\n{report.read_text(errors='replace')}")


if __name__ == "__main__":
    main()
