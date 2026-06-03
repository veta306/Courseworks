"use client";

import { useState } from "react";
import { Button } from "@mui/material";

type FileInfo = {
    fileName: string;
    fileUrl: string;
};

export default function RefreshPdfButton({ workId }: { workId: string }) {

    const [files, setFiles] = useState<FileInfo[]>([]);
    const [selectedFile, setSelectedFile] = useState<FileInfo | null>(null);

    // 1. Отримати список файлів
    const handleFetchFiles = async () => {
        const response = await fetch(
            `${process.env.NEXT_PUBLIC_API_URL}/works/${workId}/refresh-pdf`,
            { method: "POST", credentials: "include" }
        );

        const data: FileInfo[] = await response.json();
        setFiles(data);
    };

    // 2. Вибір файлу
    const handleSelect = async (file: FileInfo) => {
        setSelectedFile(file);

        // 3. Відправка вибору на backend
        await fetch(
            `${process.env.NEXT_PUBLIC_API_URL}/works/${workId}/select-pdf`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                credentials: "include",
                body: JSON.stringify({
                    fileName: file.fileName,
                    fileUrl: file.fileUrl
                })
            }
        );
    };

    return (
        <div>
            <Button variant="contained" onClick={handleFetchFiles}>
                Оновити PDF
            </Button>

            {/* список файлів */}
            <ul>
                {files.map((file) => (
                    <li key={file.fileName}>
                        <button onClick={() => handleSelect(file)}>
                            {file.fileName}
                        </button>
                    </li>
                ))}
            </ul>

            {/* відображення PDF */}
            {selectedFile && (
                <iframe
                    src={selectedFile.fileUrl}
                    width="100%"
                    height="600px"
                />
            )}
        </div>
    );
}