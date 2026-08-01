import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = path.resolve(process.argv[2] || "outputs/yliyun-p0");
await fs.mkdir(outputDir, { recursive: true });

const workbook = Workbook.create();
const sheet = workbook.worksheets.add("Fixture");
sheet.showGridLines = false;
sheet.freezePanes.freezeRows(3);

sheet.getRange("A1:D1").merge();
sheet.getRange("A1").values = [["MateClaw Cloud Integration Fixture"]];
sheet.getRange("A1:D1").format = {
  fill: "#0F4C5C",
  font: { bold: true, color: "#FFFFFF" },
  verticalAlignment: "center",
};
sheet.getRange("A1:D1").format.rowHeight = 28;

sheet.getRange("A2:D2").merge();
sheet.getRange("A2").values = [[
  "Deterministic XLSX sample - MATECLAW_FIXTURE_ALPHA_2026",
]];
sheet.getRange("A2:D2").format = {
  fill: "#E8F1F2",
  font: { color: "#334155", italic: true },
  verticalAlignment: "center",
};

sheet.getRange("A3:D6").values = [
  ["Item", "Quantity", "Unit Price", "Amount"],
  ["Alpha", 2, 12.5, null],
  ["Beta", 3, 8, null],
  ["Total", null, null, null],
];
sheet.getRange("D4").formulas = [["=B4*C4"]];
sheet.getRange("D4:D5").fillDown();
sheet.getRange("D6").formulas = [["=SUM(D4:D5)"]];

sheet.getRange("A3:D3").format = {
  fill: "#2E7490",
  font: { bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  borders: { preset: "outside", style: "thin", color: "#1F4D78" },
};
sheet.getRange("A4:D6").format = {
  borders: {
    insideHorizontal: { style: "thin", color: "#D9E2E8" },
    bottom: { style: "thin", color: "#94A3B8" },
  },
  verticalAlignment: "center",
};
sheet.getRange("A6:D6").format = {
  fill: "#F1F5F9",
  font: { bold: true, color: "#0F172A" },
  borders: { preset: "doubleBottom", style: "thin", color: "#334155" },
};
sheet.getRange("B4:B5").format.numberFormat = "#,##0";
sheet.getRange("C4:D6").format.numberFormat = "$#,##0.00";
sheet.getRange("B4:D6").format.horizontalAlignment = "right";
sheet.getRange("A1:A6").format.columnWidth = 26;
sheet.getRange("B1:B6").format.columnWidth = 13;
sheet.getRange("C1:C6").format.columnWidth = 15;
sheet.getRange("D1:D6").format.columnWidth = 16;
sheet.getRange("A2:D6").format.wrapText = true;
sheet.getRange("A2:D6").format.autofitRows();

const rangeCheck = await workbook.inspect({
  kind: "table",
  range: "Fixture!A1:D6",
  include: "values,formulas",
  tableMaxRows: 10,
  tableMaxCols: 6,
  maxChars: 4000,
});
const formulaErrors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 50 },
  summary: "fixture formula error scan",
  maxChars: 2000,
});

const preview = await workbook.render({
  sheetName: "Fixture",
  range: "A1:D6",
  scale: 2,
  format: "png",
});
await fs.writeFile(
  path.join(outputDir, "fixture-sample-xlsx.png"),
  new Uint8Array(await preview.arrayBuffer()),
);

const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(path.join(outputDir, "fixture-sample.xlsx"));

process.stdout.write(JSON.stringify({
  outputDir,
  rangeCheck: rangeCheck.ndjson,
  formulaErrors: formulaErrors.ndjson,
}) + "\n");
