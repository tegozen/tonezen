import { useEffect, useState } from "react";
import { ProfileIcon } from "@/shared/ui/TonezenIcons";

interface ProfileAvatarProps {
  avatarUrl?: string | null;
  size?: number;
  iconSize?: number;
}

export function ProfileAvatar({ avatarUrl, size = 58, iconSize = 28 }: ProfileAvatarProps) {
  const [imageFailed, setImageFailed] = useState(false);

  useEffect(() => {
    setImageFailed(false);
  }, [avatarUrl]);

  const showImage = Boolean(avatarUrl) && !imageFailed;

  return (
    <div className="profile-avatar-ring shrink-0" style={{ width: size, height: size }}>
      <div className="profile-avatar-inner">
        {showImage ? (
          <img
            src={avatarUrl ?? undefined}
            alt=""
            className="h-full w-full object-cover"
            onError={() => setImageFailed(true)}
          />
        ) : (
          <ProfileIcon className="text-teal" style={{ width: iconSize, height: iconSize }} />
        )}
      </div>
    </div>
  );
}
