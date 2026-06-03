import CopyButton from "@/components/CopyButton";
import { Work } from "@/types/dto";
import {
  Grid,
  Link,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
  Typography,
} from "@mui/material";
import { Metadata } from "next";
import { cookies } from "next/headers";
import { notFound } from "next/navigation";
import RefreshPdfButton from "@/components/RefreshPdfButton";

export const metadata: Metadata = {
  title: "Робота",
};

export default async function WorkPage({
  params,
}: {
  params: Promise<{ workId: string }>;
}) {
  const { workId } = await params;
  const cookieStore = await cookies();
  const response = await fetch(
    `${process.env.NEXT_PUBLIC_API_URL}/works/${workId}`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
        Cookie: `JSESSIONID=${cookieStore.get("JSESSIONID")?.value}`,
      },
    }
  );

  const work: Work = response.ok ? await response.json() : null;
  if (!work) notFound();

  return (
    <Grid container spacing={3} padding={3}>
      <Grid size={6}>
        <TableContainer component={Paper}>
          <Table>
            <TableBody>
              <TableRow>
                <TableCell>File</TableCell>
                <TableCell>*{work.externalIdCode}*Додатків*.pdf</TableCell>
                <TableCell align="right">
                  <CopyButton textToCopy={`*${work.externalIdCode ?? ""}*Додатків*.pdf`} />
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>File</TableCell>
                <TableCell>{work.fileNameToCopy ?? "*Додатків*.pdf"}</TableCell>
                <TableCell align="right">
                  <CopyButton textToCopy={work.fileNameToCopy ?? "*Додатків*.pdf"}/>
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Назва дисципліни</TableCell>
                <TableCell colSpan={2}
                    dangerouslySetInnerHTML={{ __html: work.nameAtTitlePageDifference }}
                />
              </TableRow>
              <TableRow>
                <TableCell rowSpan={2}>Тема</TableCell>
                <TableCell id="work-theme-from-table">{work.theme}</TableCell>
                <TableCell align="right">
                  <CopyButton textToCopy={work.theme ?? ""} />
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell
                  id="work-theme-from-file"
                  colSpan={2}
                  dangerouslySetInnerHTML={{ __html: work.themeDifference }}
                />
              </TableRow>
              <TableRow>
                <TableCell rowSpan={3}>Студент</TableCell>
                <TableCell>{work.rawStudentName}</TableCell>
                <TableCell align="right">
                  <CopyButton textToCopy={work.rawStudentName} />
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>{work.student.name}</TableCell>
                <TableCell align="right">
                  <CopyButton textToCopy={work.student.name} />
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell
                  colSpan={2}
                  dangerouslySetInnerHTML={{ __html: work.studentDifference }}
                />
              </TableRow>
              <TableRow>
                <TableCell rowSpan={3}>Керівник</TableCell>
                <TableCell>{work.rawSupervisorName}</TableCell>
                <TableCell align="right">
                  <CopyButton textToCopy={work.rawSupervisorName} />
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>{work.supervisor?.name}</TableCell>
                <TableCell align="right">
                  <CopyButton textToCopy={work.supervisor?.name ?? ""} />
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell
                  colSpan={3}
                  dangerouslySetInnerHTML={{
                    __html: work.supervisorDifference ?? "",
                  }}
                />
              </TableRow>
              <TableRow>
                <TableCell>Міністерство</TableCell>
                <TableCell
                  colSpan={2}
                  dangerouslySetInnerHTML={{
                    __html: work.ministryDifference ?? "",
                  }}
                />
              </TableRow>
              <TableRow>
                <TableCell>ЗВО</TableCell>
                <TableCell
                  colSpan={2}
                  dangerouslySetInnerHTML={{ __html: work.heidifference ?? "" }}
                />
              </TableRow>
              <TableRow>
                <TableCell>Кафедра</TableCell>
                <TableCell
                  colSpan={2}
                  dangerouslySetInnerHTML={{
                    __html: work.departmentDifference ?? "",
                  }}
                />
              </TableRow>
              <TableRow>
                <TableCell>Група</TableCell>
                <TableCell
                  colSpan={2}
                  dangerouslySetInnerHTML={{
                    __html: work.groupDifference ?? "",
                  }}
                />
              </TableRow>
              <TableRow>
                <TableCell>Місто, рік</TableCell>
                <TableCell
                  colSpan={2}
                  dangerouslySetInnerHTML={{
                    __html: work.cityYearDifference ?? "",
                  }}
                />
              </TableRow>
              <TableRow>
                <TableCell colSpan={2}>
                  <Link href={work.topicDistributionLink} target="_blank">
                    Link на таблицю з темами
                  </Link>
                </TableCell>
                <TableCell align="right">
                  <Link href={work.googleSubmissionLink} target="_blank">
                    Link на здачу в гуглоклас
                  </Link>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </TableContainer>
      </Grid>
      <Grid size={3}>
        <Table>
          <TableBody>
            <TableRow>
              <Paper sx={{ p: 2, height: "100%" }}>
                <Typography variant="h6">Попередній перегляд (оригінал)</Typography>
                <iframe
                  src={work.classroomLink.replace(/\/view.*$/, "/preview")}
                  className="w-full h-[96%]"
                ></iframe>
              </Paper>
            </TableRow>
            <TableRow>
              <RefreshPdfButton workId={workId} />
            </TableRow>
          </TableBody>
        </Table>
      </Grid>
      <Grid size={3}>
        <Table>
          <TableBody>
            <TableRow>
              <Paper sx={{ p: 2, height: "100%" }}>
                <Typography variant="h6">Попередній перегляд (без додатків)</Typography>
                <iframe
                  src={(work.shortTextLink ?? "").replace(/\/view.*$/, "/preview")}
                  className="w-full h-[96%]"
                ></iframe>
              </Paper>
            </TableRow>
          </TableBody>
        </Table>
      </Grid>
    </Grid>
  );
}
