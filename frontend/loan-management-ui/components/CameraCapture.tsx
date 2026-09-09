"use client";

import { useCallback, useEffect, useRef, useState } from "react";

interface CameraCaptureProps {
  onCapture: (blob: Blob) => void;
  onClose: () => void;
  primary?: string;
}

function cameraErrorMessage(error: unknown): string {
  const name =
    error && typeof error === "object" && "name" in error
      ? String((error as { name?: unknown }).name ?? "")
      : "";

  switch (name) {
    case "NotAllowedError":
    case "PermissionDeniedError":
      return "Camera permission was denied. Allow camera access for this website in your browser settings, then tap Try Camera Again.";
    case "NotFoundError":
    case "DevicesNotFoundError":
      return "No camera was found on this device. Please connect or enable a camera, or use the photo upload option.";
    case "NotReadableError":
    case "TrackStartError":
      return "Your camera is already being used by another app or browser tab. Close the other camera app/tab and try again.";
    case "OverconstrainedError":
      return "The front camera is not available with the requested settings. We will retry with the device's default camera.";
    case "SecurityError":
      return "The browser blocked camera access for security reasons. Open this page directly over HTTPS and allow camera access.";
    case "AbortError":
      return "Camera startup was interrupted. Tap Try Camera Again.";
    default:
      return "We couldn't access your camera. Please allow camera access for this website, then try again. You can also use the photo upload option.";
  }
}

export default function CameraCapture({
  onCapture,
  onClose,
  primary = "#0D6B3E",
}: CameraCaptureProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const previewUrlRef = useRef<string | null>(null);
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const [starting, setStarting] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);
  const [capturedBlob, setCapturedBlob] = useState<Blob | null>(null);

  const stopCamera = useCallback(() => {
    const stream = streamRef.current;
    if (stream) {
      stream.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    setReady(false);
  }, []);

  const startCamera = useCallback(async () => {
    setError("");
    setStarting(true);
    setReady(false);
    stopCamera();

    if (
      typeof window === "undefined" ||
      !navigator.mediaDevices?.getUserMedia
    ) {
      setStarting(false);
      setError(
        "Camera access is not available in this browser. Please use the photo upload option instead.",
      );
      return;
    }

    if (!window.isSecureContext) {
      setStarting(false);
      setError(
        "Camera access requires a secure HTTPS page. Please open the loan application using HTTPS, then try again.",
      );
      return;
    }

    try {
      let stream: MediaStream;

      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: { ideal: "user" },
            width: { ideal: 1280 },
            height: { ideal: 1280 },
          },
          audio: false,
        });
      } catch (firstError) {
        // Some devices do not expose the requested front-camera constraint.
        // Retry with the least restrictive video constraint before failing.
        const name =
          firstError && typeof firstError === "object" && "name" in firstError
            ? String((firstError as { name?: unknown }).name ?? "")
            : "";

        if (name !== "OverconstrainedError") {
          throw firstError;
        }

        stream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: false,
        });
      }

      streamRef.current = stream;

      const video = videoRef.current;
      if (!video) {
        stream.getTracks().forEach((track) => track.stop());
        streamRef.current = null;
        throw new Error("Camera preview could not be initialized.");
      }

      video.srcObject = stream;
      video.muted = true;
      video.playsInline = true;

      try {
        await video.play();
      } catch {
        // The user can still press play on browsers that block autoplay.
      }

      setReady(true);
    } catch (cameraError) {
      setError(cameraErrorMessage(cameraError));
      stopCamera();
    } finally {
      setStarting(false);
    }
  }, [stopCamera]);

  useEffect(() => {
    void startCamera();

    return () => {
      stopCamera();
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current);
        previewUrlRef.current = null;
      }
    };
  }, [startCamera, stopCamera]);

  const capture = () => {
    const video = videoRef.current;
    const canvas = canvasRef.current;

    if (!video || !canvas || !ready) return;

    if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
      setError(
        "The camera is still starting. Please wait a moment and try again.",
      );
      return;
    }

    const width = video.videoWidth;
    const height = video.videoHeight;

    if (!width || !height) {
      setError(
        "The camera preview is not ready yet. Please wait a moment and try again.",
      );
      return;
    }

    canvas.width = width;
    canvas.height = height;

    const context = canvas.getContext("2d");
    if (!context) {
      setError("Could not capture the camera image. Please try again.");
      return;
    }

    context.save();
    // The preview is mirrored for a natural selfie experience. Mirror the
    // captured image too so the saved selfie matches what the applicant sees.
    context.translate(width, 0);
    context.scale(-1, 1);
    context.drawImage(video, 0, 0, width, height);
    context.restore();

    canvas.toBlob(
      (blob) => {
        if (!blob) {
          setError("Could not create the selfie image. Please try again.");
          return;
        }

        if (previewUrlRef.current) {
          URL.revokeObjectURL(previewUrlRef.current);
        }

        const url = URL.createObjectURL(blob);
        previewUrlRef.current = url;
        setCapturedBlob(blob);
        setPreview(url);
        stopCamera();
      },
      "image/jpeg",
      0.9,
    );
  };

  const retake = () => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = null;
    }

    setPreview(null);
    setCapturedBlob(null);
    void startCamera();
  };

  const confirm = () => {
    if (capturedBlob) {
      onCapture(capturedBlob);
    }
  };

  const handleFallbackFile = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      onCapture(file);
    }
    event.target.value = "";
  };

  const close = () => {
    stopCamera();
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-xl max-w-md w-full overflow-hidden shadow-2xl">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="font-bold text-gray-900">Take a Selfie</div>
          <button
            type="button"
            onClick={close}
            className="text-gray-400 hover:text-gray-600 text-xl leading-none"
            aria-label="Close camera"
          >
            ×
          </button>
        </div>

        <div className="p-5">
          <p className="text-xs text-gray-500 mb-3">
            Face the camera in good lighting, remove sunglasses or hats, and
            center your face in the frame.
          </p>

          {error ? (
            <div className="space-y-4">
              <div className="rounded-lg border border-red-200 bg-red-50 p-3">
                <p className="text-sm text-red-700">{error}</p>
              </div>

              <button
                type="button"
                onClick={() => void startCamera()}
                disabled={starting}
                className="w-full py-3 rounded-md text-sm font-bold text-white disabled:opacity-50"
                style={{ backgroundColor: primary }}
              >
                {starting ? "Starting Camera…" : "Try Camera Again"}
              </button>

              <label
                className="block text-center py-3 rounded-md text-sm font-bold cursor-pointer border"
                style={{ borderColor: primary, color: primary }}
              >
                Upload a Photo Instead
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/*"
                  capture="user"
                  className="hidden"
                  onChange={handleFallbackFile}
                />
              </label>
            </div>
          ) : (
            <>
              <div className="relative rounded-lg overflow-hidden bg-gray-900 aspect-square mb-4">
                {!preview ? (
                  <video
                    ref={videoRef}
                    autoPlay
                    playsInline
                    muted
                    className="w-full h-full object-cover -scale-x-100"
                    onClick={() => {
                      if (!ready && !starting) void startCamera();
                    }}
                  />
                ) : (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={preview}
                    alt="Captured selfie"
                    className="w-full h-full object-cover"
                  />
                )}

                {!ready && !preview && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center text-white/80 text-sm gap-3 bg-black/20">
                    <span>
                      {starting ? "Starting camera…" : "Camera is not ready"}
                    </span>
                    {!starting && (
                      <button
                        type="button"
                        onClick={() => void startCamera()}
                        className="px-4 py-2 rounded-md text-xs font-bold text-white"
                        style={{ backgroundColor: primary }}
                      >
                        Start Camera
                      </button>
                    )}
                  </div>
                )}
              </div>

              <canvas ref={canvasRef} className="hidden" />

              {!preview ? (
                <button
                  type="button"
                  onClick={capture}
                  disabled={!ready || starting}
                  className="w-full py-3 rounded-md text-sm font-bold text-white disabled:opacity-50"
                  style={{ backgroundColor: primary }}
                >
                  Capture Photo
                </button>
              ) : (
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={retake}
                    className="flex-1 py-3 rounded-md text-sm font-bold border border-gray-300 text-gray-700"
                  >
                    Retake
                  </button>
                  <button
                    type="button"
                    onClick={confirm}
                    className="flex-1 py-3 rounded-md text-sm font-bold text-white"
                    style={{ backgroundColor: primary }}
                  >
                    Use This Photo
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
