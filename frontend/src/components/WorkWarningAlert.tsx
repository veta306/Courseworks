"use client";
import React, { useState } from "react";
import { Alert, AlertTitle, Button, CircularProgress } from "@mui/material";
import AddIcon from "@mui/icons-material/Refresh";
import { toast } from "react-toastify";
import { useRouter } from "next/navigation";

interface WorkWarningAlertProps {
  workId: number;
  oldDate: string;
  newDate: string;
}

const WorkWarningAlert = ({
  workId,
  oldDate,
  newDate,
}: WorkWarningAlertProps) => {
  const router = useRouter();
  const [isUpdating, setIsUpdating] = useState(false);
  const onUpdate = async () => {
    setIsUpdating(true);
    toast.info("Оновлення роботи...");
    try {
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/manager/works/${workId}/update`,
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify({ workId }),
        },
      );
      if (response.ok) {
        toast.success("Роботу успішно оновлено!");
        router.refresh();
      } else {
        toast.error("Помилка оновлення роботи");
      }
    } catch (error) {
      toast.error("Помилка оновлення роботи " + error);
    } finally {
      setIsUpdating(false);
    }
  };
  return (
    <Alert
      severity="warning"
      sx={{ display: "flex", flexDirection: "column", alignItems: "center" }}
    >
      <AlertTitle>В гугл класі з`явилася нова версія роботи.</AlertTitle>
      <div>
        В системі версія, яка здана {oldDate}
        <br />В класрумі версія, яка здана {newDate}
      </div>
      <Button
        variant="outlined"
        color="success"
        sx={{ width: "100%", mt: 2 }}
        startIcon={isUpdating ? <CircularProgress size={16} /> : <AddIcon />}
        onClick={onUpdate}
        disabled={isUpdating}
      >
        {"Оновити цю роботу"}
      </Button>
    </Alert>
  );
};

export default WorkWarningAlert;
