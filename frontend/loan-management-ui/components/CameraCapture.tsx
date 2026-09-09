"use client";

import { useCallback, useEffect, useRef, useState } from "react";

interface CameraCaptureProps {
  onCapture: (file: File) => void;
  onClose: () => void;
  primary?: string;
}

function cameraErrorMessage(error: unknown): string {
  if (!error || typeof error !== "object") {
    return "We could not access your camera. Please check your browser permissions and try again.";
  }

  const name =
    "name" in error ? String((error as { name?: unknown }).name) : "";

  switch (name) {
    case "NotAllowedError":
    case "PermissionDeniedError":
      return "Camera permission was denied. Allow camera access for this site in your browser settings, then click “Try Camera Again”.";

    case "NotFoundError":
    case "DevicesNotFoundError":
      return "No camera was found on this device. You can use the photo upload option instead.";

    case "NotReadableError":
    case "TrackStartError":
      return "Your camera is already being used by another application. Close other camera/video apps and try again.";

    case "OverconstrainedError":
      return "This camera does not support the requested settings. We will try the camera again with compatible settings.";

    case "SecurityError":
      return "Camera access was blocked by the browser security policy. Open this application directly over HTTPS and try again.";

    case "AbortError":
      return "Camera startup was interrupted. Please try again.";

    default:
      return "We could not access your camera. Please check your browser permissions and try again.";
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
  const [starting, setStarting] = useState(true);
  const [preview, setPreview] = useState<string | null>(null);
  const [capturedFile, setCapturedFile] = useState<File | null>(null);

  const stopCamera = useCallback(() => {
    const stream = streamRef.current;

    if (stream) {
      stream.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }

    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }

    setReady(false);
  }, []);

  const startCamera = useCallback(async () => {
    stopCamera();

    setError("");
    setReady(false);
    setStarting(true);

    if (typeof window === "undefined" || typeof navigator === "undefined") {
      setStarting(false);
      setError("Camera access is only available in a browser.");
      return;
    }

    if (!window.isSecureContext) {
      setStarting(false);
      setError(
        "Camera access requires a secure HTTPS connection. Please open the application using HTTPS, then try again.",
      );
      return;
    }

    if (!navigator.mediaDevices?.getUserMedia) {
      setStarting(false);
      setError(
        "Camera access is not supported by this browser. You can use the photo upload option instead.",
      );
      return;
    }

    let stream: MediaStream | null = null;

    try {
      /*
       * Prefer the front-facing camera for a selfie. "ideal" constraints
       * allow the browser to choose a compatible camera instead of failing
       * when a device does not support an exact resolution.
       */
      stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: {
          facingMode: { ideal: "user" },
          width: { ideal: 1280 },
          height: { ideal: 1280 },
        },
      });
    } catch (firstError) {
      /*
       * Some desktop webcams reject the facingMode constraint. Retry with
       * the most compatible video constraint before showing an error.
       */
      if (
        firstError &&
        typeof firstError === "object" &&
        "name" in firstError &&
        String((firstError as { name?: unknown }).name) ===
          "OverconstrainedError"
      ) {
        try {
          stream = await navigator.mediaDevices.getUserMedia({
            audio: false,
            video: true,
          });
        } catch (secondError) {
          setStarting(false);
          setError(cameraErrorMessage(secondError));
          return;
        }
      } else {
        setStarting(false);
        setError(cameraErrorMessage(firstError));
        return;
      }
    }

    if (!stream) {
      setStarting(false);
      setError(
        "No camera stream was returned by the browser. Please try again.",
      );
      return;
    }

    streamRef.current = stream;

    const video = videoRef.current;

    if (!video) {
      stream.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
      setStarting(false);
      setError(
        "The camera preview could not be initialized. Please try again.",
      );
      return;
    }

    video.srcObject = stream;
    video.muted = true;
    video.playsInline = true;

    try {
      await video.play();
    } catch {
      /*
       * The browser may require another rendering cycle before play().
       * The stream remains attached and the video element can still start
       * automatically because it is muted and playsInline.
       */
    }

    setStarting(false);
    setReady(true);
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

    if (!video || !canvas || !ready) {
      setError(
        "The camera is not ready yet. Please wait a moment and try again.",
      );
      return;
    }

    if (video.videoWidth <= 0 || video.videoHeight <= 0) {
      setError("The camera preview is not ready. Please try again.");
      return;
    }

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    const context = canvas.getContext("2d");

    if (!context) {
      setError("Could not prepare the selfie image. Please try again.");
      return;
    }

    /*
     * The live preview is mirrored. Mirror the captured image too so the
     * applicant sees the same orientation in the confirmation screen.
     */
    context.save();
    context.translate(canvas.width, 0);
    context.scale(-1, 1);
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    context.restore();

    canvas.toBlob(
      (blob) => {
        if (!blob || blob.size === 0) {
          setError("The selfie image could not be created. Please try again.");
          return;
        }

        const now = Date.now();
        const file = new File([blob], `selfie-${now}.jpg`, {
          type: "image/jpeg",
          lastModified: now,
        });

        if (previewUrlRef.current) {
          URL.revokeObjectURL(previewUrlRef.current);
        }

        const url = URL.createObjectURL(file);
        previewUrlRef.current = url;

        setCapturedFile(file);
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
    setCapturedFile(null);
    void startCamera();
  };

  const confirm = () => {
    if (!capturedFile || capturedFile.size === 0) {
      setError("Please capture a valid selfie before continuing.");
      return;
    }

    onCapture(capturedFile);
  };

  const handleFallbackFile = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];

    if (!file) {
      return;
    }

    if (!file.type.startsWith("image/")) {
      setError("Please select an image file for the selfie.");
      event.target.value = "";
      return;
    }

    if (file.size === 0) {
      setError("The selected image is empty. Please choose another photo.");
      event.target.value = "";
      return;
    }

    onCapture(file);
    event.target.value = "";
  };

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-xl max-w-md w-full overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="font-bold text-gray-900">Take a Selfie</div>

          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-xl leading-none"
            aria-label="Close camera"
          >
            ×
          </button>
        </div>

        <div className="p-5">
          {error ? (
            <div>
              <p className="text-sm text-red-600 mb-4">{error}</p>

              <div className="space-y-2">
                <button
                  type="button"
                  onClick={() => void startCamera()}
                  className="w-full py-3 rounded-md text-sm font-bold text-white"
                  style={{ backgroundColor: primary }}
                >
                  Try Camera Again
                </button>

                <label className="block w-full text-center py-3 rounded-md text-sm font-bold border border-gray-300 text-gray-700 cursor-pointer">
                  Upload a Photo Instead
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    capture="user"
                    className="hidden"
                    onChange={handleFallbackFile}
                  />
                </label>
              </div>
            </div>
          ) : (
            <>
              <p className="text-xs text-gray-500 mb-3">
                Face the camera in good lighting, remove sunglasses or hats, and
                center your face in the frame.
              </p>

              <div className="relative rounded-lg overflow-hidden bg-gray-900 aspect-square mb-4">
                {!preview ? (
                  <video
                    ref={videoRef}
                    autoPlay
                    playsInline
                    muted
                    className="w-full h-full object-cover -scale-x-100"
                  />
                ) : (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={preview}
                    alt="Captured selfie"
                    className="w-full h-full object-cover"
                  />
                )}

                {starting && !preview && (
                  <div className="absolute inset-0 flex items-center justify-center text-white/70 text-sm">
                    Starting camera…
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
                  {starting ? "Starting Camera…" : "Capture Photo"}
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
