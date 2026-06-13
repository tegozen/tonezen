import type { CSSProperties, HTMLAttributes } from "react";
import checkCircleUrl from "../assets/icons/check-circle.svg";
import chevronLeftUrl from "../assets/icons/chevron-left.svg";
import chevronRightUrl from "../assets/icons/chevron-right.svg";
import closeUrl from "../assets/icons/close.svg";
import downloadUrl from "../assets/icons/download.svg";
import eyeOffUrl from "../assets/icons/eye-off.svg";
import eyeUrl from "../assets/icons/eye.svg";
import filterUrl from "../assets/icons/filter.svg";
import forward15Url from "../assets/icons/forward-15.svg";
import gripUrl from "../assets/icons/grip.svg";
import heartUrl from "../assets/icons/heart.svg";
import libraryUrl from "../assets/icons/library.svg";
import lockUrl from "../assets/icons/lock.svg";
import mailUrl from "../assets/icons/mail.svg";
import moreVerticalUrl from "../assets/icons/more-vertical.svg";
import pauseUrl from "../assets/icons/pause.svg";
import playUrl from "../assets/icons/play.svg";
import playerUrl from "../assets/icons/player.svg";
import profileUrl from "../assets/icons/profile.svg";
import queueUrl from "../assets/icons/queue.svg";
import rewind15Url from "../assets/icons/rewind-15.svg";
import searchUrl from "../assets/icons/search.svg";
import skipNextUrl from "../assets/icons/skip-next.svg";
import skipPreviousUrl from "../assets/icons/skip-previous.svg";
import storageUrl from "../assets/icons/storage.svg";
import syncUrl from "../assets/icons/sync.svg";
import trashUrl from "../assets/icons/trash.svg";
import warningUrl from "../assets/icons/warning.svg";

type IconProps = HTMLAttributes<HTMLSpanElement> & {
  title?: string;
};

function createTonezenIcon(iconUrl: string) {
  return function TonezenIcon({ title, className, style, ...props }: IconProps) {
    const maskStyle: CSSProperties = {
      WebkitMask: `url(${iconUrl}) center / contain no-repeat`,
      mask: `url(${iconUrl}) center / contain no-repeat`,
      backgroundColor: "currentColor",
      ...style,
    };

    return (
      <span
        aria-hidden={title ? undefined : true}
        aria-label={title}
        role={title ? "img" : undefined}
        className={`inline-block shrink-0 ${className ?? ""}`}
        style={maskStyle}
        {...props}
      />
    );
  };
}

export const LibraryIcon = createTonezenIcon(libraryUrl);
export const PlayerIcon = createTonezenIcon(playerUrl);
export const DownloadsIcon = createTonezenIcon(downloadUrl);
export const ProfileIcon = createTonezenIcon(profileUrl);
export const SearchIcon = createTonezenIcon(searchUrl);
export const FilterIcon = createTonezenIcon(filterUrl);
export const MoreVerticalIcon = createTonezenIcon(moreVerticalUrl);
export const PlayIcon = createTonezenIcon(playUrl);
export const PauseIcon = createTonezenIcon(pauseUrl);
export const Rewind15Icon = createTonezenIcon(rewind15Url);
export const Forward15Icon = createTonezenIcon(forward15Url);
export const SkipBackIcon = createTonezenIcon(skipPreviousUrl);
export const SkipForwardIcon = createTonezenIcon(skipNextUrl);
export const QueueIcon = createTonezenIcon(queueUrl);
export const HeartIcon = createTonezenIcon(heartUrl);
export const CheckCircleIcon = createTonezenIcon(checkCircleUrl);
export const ChevronLeftIcon = createTonezenIcon(chevronLeftUrl);
export const ChevronRightIcon = createTonezenIcon(chevronRightUrl);
export const CloseIcon = createTonezenIcon(closeUrl);
export const EyeIcon = createTonezenIcon(eyeUrl);
export const EyeOffIcon = createTonezenIcon(eyeOffUrl);
export const MailIcon = createTonezenIcon(mailUrl);
export const LockIcon = createTonezenIcon(lockUrl);
export const StorageIcon = createTonezenIcon(storageUrl);
export const SyncIcon = createTonezenIcon(syncUrl);
export const WarningIcon = createTonezenIcon(warningUrl);
export const TrashIcon = createTonezenIcon(trashUrl);
export const GripIcon = createTonezenIcon(gripUrl);
