import { get } from "./api";
import { DashboardStats, ChartPoint } from "../types/index";

export const getDashboardStats = async (): Promise<DashboardStats> => {
  try {
    const response = await get("/dashboard/stats");

    if (response && typeof response === "object") {
      return response as DashboardStats;
    }
  } catch (error) {
    console.warn(
      "[DASHBOARD] /dashboard/stats unavailable; falling back to /loans/dashboard",
      error,
    );
  }

  const fallback = await get("/loans/dashboard");

  return fallback as DashboardStats;
};

export const getLoanChartData = (): Promise<ChartPoint[]> =>
  get("/dashboard/charts/loans") as Promise<ChartPoint[]>;

export const getCollectionChart = (): Promise<ChartPoint[]> =>
  get("/dashboard/charts/collections") as Promise<ChartPoint[]>;
