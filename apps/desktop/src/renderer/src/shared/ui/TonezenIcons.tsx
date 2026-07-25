import type { CSSProperties, HTMLAttributes } from "react";
import {
  booksUrl,
  checkCircleUrl,
  chevronLeftUrl,
  chevronRightUrl,
  closeUrl,
  downloadUrl,
  eyeOffUrl,
  eyeUrl,
  filterUrl,
  forward15Url,
  gripUrl,
  heartUrl,
  lockUrl,
  mailUrl,
  moreVerticalUrl,
  musicUrl,
  pauseUrl,
  playUrl,
  playerUrl,
  profileUrl,
  queueUrl,
  rewind15Url,
  searchUrl,
  skipNextUrl,
  skipPreviousUrl,
  storageUrl,
  syncUrl,
  trashUrl,
  warningUrl,
} from "./icons";

type IconProps = HTMLAttributes<HTMLSpanElement> & {
  title?: string;
};

function createTonezenIcon(iconUrl: string) {
  return function TonezenIcon({ title, className, style, ...props }: IconProps) {
    const maskStyle: CSSProperties = {
      WebkitMask: `url("${iconUrl}") center / contain no-repeat`,
      mask: `url("${iconUrl}") center / contain no-repeat`,
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

export const MusicIcon = createTonezenIcon(musicUrl);
export const BooksIcon = createTonezenIcon(booksUrl);
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
